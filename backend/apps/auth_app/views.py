import hashlib, random, string
from datetime import timedelta
from django.utils import timezone
from django.conf import settings
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated, BasePermission
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework.throttling import ScopedRateThrottle
from django.contrib.auth.hashers import check_password

from .models import Officer, OtpRecord, OfficerActivity, NotificationLog, OfficerProfile, OfficerCredential

class NotificationAckView(APIView):
    """
    POST /api/auth/notification-ack
    { notif_id, type: 'DELIVERED' | 'OPENED' }
    Logs when a notification was actually shown or opened by the user.
    """
    def post(self, request):
        notif_id = request.data.get('notif_id')
        ack_type = request.data.get('type')
        
        try:
            log = NotificationLog.objects.get(id=notif_id, officer=request.user)
            if ack_type == 'DELIVERED':
                log.acked_at = timezone.now()
            elif ack_type == 'OPENED':
                log.opened_at = timezone.now()
            log.save()
            return Response({"status": "acked"})
        except NotificationLog.DoesNotExist:
            return Response({"error": "Log not found"}, status=404)


def _hash_otp(otp: str) -> str:
    return hashlib.sha256(otp.encode()).hexdigest()


def _get_client_ip(request) -> str:
    x_forwarded = request.META.get('HTTP_X_FORWARDED_FOR')
    return x_forwarded.split(',')[0] if x_forwarded else request.META.get('REMOTE_ADDR', '')


def _audit(officer, action, request, **metadata):
    # Mask sensitive keys
    sensitive_keys = ['otp', 'password', 'token', 'refresh_token']
    safe_metadata = {k: ('***' if k.lower() in sensitive_keys else v) for k, v in metadata.items()}
    
    import hashlib
    device_id = request.data.get('device_id', '')
    
    OfficerActivity.objects.create(
        officer    = officer,
        actor      = metadata.get('actor'),
        unit       = officer.unit if officer else metadata.get('unit'),
        pno        = officer.pno if officer else '',
        action     = action,
        severity   = metadata.get('severity', 'INFO'),
        route      = request.path,
        result     = 'SUCCESS',
        ip_address = _get_client_ip(request),
        device_id_hash = hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
        metadata   = safe_metadata
    )


class LoginView(APIView):
    """
    Step 1: POST /api/login
    { pno, device_id }

    Server checks:
    ① Officer exists and is active
    ② Not blocked
    ③ Device binding: if device already registered, must match
    ④ Rate limit: max 5 attempts per hour
    ⑤ Generates OTP → sends via SMS
    """
    permission_classes = [AllowAny]
    throttle_scope     = 'otp'

    def post(self, request):
        from .services.auth_service import AuthService
        pno       = request.data.get('pno', '').strip()
        password  = request.data.get('password', '').strip()
        device_id = request.data.get('device_id', '').strip()

        if password:
            result, error = AuthService.login(pno, password, device_id)
            if error:
                return Response({'status': 'error', 'message': error}, status=401)
            
            officer = result['user']
            profile = officer.profile
            return Response({
                'status': 'success',
                'token': result['token'],
                'refresh_token': result['refresh_token'],
                'expires_in': 3600,
                'device_secret': result['creds'].device_secret,
                'user': {
                    'id': str(officer.id),
                    'pno': officer.pno,
                    'role': officer.role,
                    'is_active': officer.is_active,
                    'must_change_password': officer.must_change_password,
                    'profile': {
                        'name': profile.name,
                        'rank': { 'code': profile.rank.code, 'name': profile.rank.name, 'level': profile.rank.level },
                        'unit': { 'code': officer.unit.code, 'name': officer.unit.name }
                    }
                }
            })

        # ── Mobile OTP Login ───────────────────────────────
        if not device_id:
            return Response({'status': 'error', 'message': 'device_id आवश्यक है'},
                            status=status.HTTP_400_BAD_REQUEST)

        try:
            officer = Officer.objects.get(pno=pno)
        except Officer.DoesNotExist:
            return Response({'status': 'error', 'message': 'PNO नहीं मिला'},
                            status=status.HTTP_404_NOT_FOUND)

        # ── Block check ────────────────────────────────────
        if officer.is_blocked:
            _audit(officer, 'LOGIN_BLOCKED', request)
            return Response({'status': 'error', 'message': 'खाता निलंबित है। Admin से संपर्क करें'},
                            status=status.HTTP_403_FORBIDDEN)

        # ── Lockout check ──────────────────────────────────
        if officer.locked_until and officer.locked_until > timezone.now():
            wait_mins = int((officer.locked_until - timezone.now()).seconds / 60) + 1
            return Response({'status': 'error',
                             'message': f'बहुत अधिक प्रयास। {wait_mins} मिनट बाद retry करें'},
                            status=status.HTTP_429_TOO_MANY_REQUESTS)

        # ── Device binding check ───────────────────────────
        if officer.device_id and officer.device_id != device_id:
            officer.failed_login_attempts += 1
            # Lock after 5 failed device attempts
            if officer.failed_login_attempts >= 5:
                officer.locked_until = timezone.now() + timedelta(minutes=15)
            officer.save(update_fields=['failed_login_attempts', 'locked_until'])
            _audit(officer, 'DEVICE_MISMATCH', request,
                   registered=officer.device_id[:8], attempted=device_id[:8])
            return Response({'status': 'error', 'message': 'device_mismatch'},
                            status=status.HTTP_403_FORBIDDEN)

        # ── OTP Cooldown & Daily Limit (Hardened) ──────────
        now = timezone.now()
        # Reset daily count if last OTP was yesterday
        if officer.last_otp_at and officer.last_otp_at.date() < now.date():
            officer.daily_otp_count = 0

        if officer.daily_otp_count >= 10:
            return Response({'status': 'error', 'message': 'दैनिक OTP सीमा समाप्त। कल प्रयास करें।'}, status=429)

        if officer.last_otp_at and (now - officer.last_otp_at).total_seconds() < 60:
            wait = 60 - int((now - officer.last_otp_at).total_seconds())
            return Response({'status': 'error', 'message': f'कृपया {wait} सेकंड बाद प्रयास करें।'}, status=429)

        # ── Generate OTP ───────────────────────────────────
        otp     = ''.join(random.choices(string.digits, k=settings.OTP_LENGTH))
        expires = timezone.now() + timedelta(minutes=settings.OTP_EXPIRY_MINUTES)

        OtpRecord.objects.create(
            officer    = officer,
            otp_hash   = _hash_otp(otp),
            device_id  = device_id,
            expires_at = expires
        )
        
        # Update trackers
        officer.last_otp_at     = now
        officer.daily_otp_count += 1
        officer.save(update_fields=['last_otp_at', 'daily_otp_count'])

        # ── Send OTP via Email ─────────────────────────────
        from django.core.mail import send_mail
        if officer.email:
            try:
                send_mail(
                    subject='KAVACH OTP Verification',
                    message=f'Your KAVACH OTP is: {otp}. It is valid for {settings.OTP_EXPIRY_MINUTES} minutes.',
                    from_email=settings.EMAIL_HOST_USER,
                    recipient_list=[officer.email],
                    fail_silently=False,
                )
            except Exception as e:
                print(f"[ERROR OTP] Could not send email to {officer.email}: {e}")
        else:
            print(f"[WARN OTP] Officer {pno} has no email address configured.")

        if settings.DEBUG:
            print(f"[DEBUG OTP] PNO={pno} OTP={otp} EMAIL={officer.email}")   # dev only — remove in production

        _audit(officer, 'OTP_SENT', request)
        return Response({'status': 'success', 'message': 'OTP Email पर भेजा गया'})


class VerifyOtpView(APIView):
    """
    Step 2: POST /api/verify-otp
    { pno, otp, device_id }

    Server checks:
    ① Valid OTP (not expired, not used)
    ② Device matches OTP record
    ③ Issues JWT (access + refresh)
    ④ Registers device if first login
    """
    permission_classes = [AllowAny]
    throttle_scope     = 'otp'

    def post(self, request):
        pno       = request.data.get('pno', '').strip()
        otp       = request.data.get('otp', '').strip()
        device_id = request.data.get('device_id', '').strip()

        try:
            officer = Officer.objects.get(pno=pno)
        except Officer.DoesNotExist:
            return Response({'status': 'error', 'message': 'PNO नहीं मिला'}, status=404)

        otp_record = OtpRecord.objects.filter(
            officer=officer, used=False, expires_at__gt=timezone.now()
        ).last()
        
        if not otp_record:
            return Response({'status': 'error', 'message': 'OTP अमान्य है या expire हो गया है'}, status=400)

        # 1. Max Attempts Check (Hardened)
        if otp_record.attempts >= 3:
            otp_record.used = True
            otp_record.save()
            return Response({'status': 'error', 'message': 'बहुत अधिक गलत प्रयास। नया OTP मंगाएं।'}, status=403)

        # 2. Verify Hash
        if not check_password(otp, otp_record.otp_hash):
            otp_record.attempts += 1
            otp_record.save()
            remaining = 3 - otp_record.attempts
            return Response({
                'status': 'error', 
                'message': f'गलत OTP। {remaining} प्रयास शेष।',
                'remaining_attempts': remaining
            }, status=400)

        # 3. Valid OTP - Burn it immediately
        otp_record.used = True
        otp_record.save()

        # 4. Hardware-Aware Device Binding Enforcement (v3)
        from .models import OfficerDevice
        
        # Check if this device is already registered
        try:
            device = OfficerDevice.objects.get(officer=officer, device_id=device_id)
            if device.status != 'ACTIVE':
                return Response({'success': False, 'code': 'DEVICE_REVOKED', 'message': 'यह device suspend कर दिया गया है। Admin से संपर्क करें।'}, status=403)
        except OfficerDevice.DoesNotExist:
            # New device registration check
            active_devices_count = OfficerDevice.objects.filter(officer=officer, status='ACTIVE').count()
            
            # Role-based limits: CO=3, PILOT=2, USER=1
            limit = 1
            if officer.role == 'COMMANDING_OFFICER': limit = 3
            elif officer.role == 'PILOT': limit = 2
            
            if active_devices_count >= limit:
                _audit(officer, 'DEVICE_LIMIT_REACHED', request, severity='WARNING', count=active_devices_count)
                return Response({
                    'success': False,
                    'code': 'DEVICE_LIMIT_EXCEEDED',
                    'message': f'अधिकतम device सीमा ({limit}) पूरी हो चुकी है। कृपया पुराने device को deactivate करें।'
                }, status=403)

            # Register new device
            device = OfficerDevice.objects.create(
                officer=officer,
                device_id=device_id,
                device_name=request.headers.get('X-Device-Model', 'Unknown Device'),
                manufacturer=request.headers.get('X-Device-Manufacturer', 'Generic'),
                android_version=request.headers.get('X-Android-Version', ''),
                app_version=request.headers.get('X-App-Version', ''),
                last_ip=_get_client_ip(request),
                status='ACTIVE',
                trust_score=100.0
            )

        # 5. Credentials Update (v3)
        cred = officer.credentials
        cred.last_login_at = timezone.now()
        cred.last_otp_at = timezone.now()
        # Risk floor rises with success
        floor = 20.0 + (cred.permanent_suspicion * 10.0)
        cred.security_risk_score = max(floor, cred.security_risk_score - 40.0)
        cred.permanent_suspicion += 1
        cred.save()

        # Signal success to middleware for decay
        request.validated_success = True

        # Reset failed attempts
        otp_record.attempts = 0
        officer.locked_until = None
        officer.save()

        # Issue JWT
        refresh = RefreshToken.for_user(officer)
        refresh['device_id'] = device_id
        refresh['session_version'] = officer.session_version

        _audit(officer, 'LOGIN_SUCCESS', request)

        profile = officer.profile
        return Response({
            'status':        'success',
            'token':         str(refresh.access_token),
            'refresh_token': str(refresh),
            'expires_in':    3600,
            'device_secret': officer.credentials.device_secret,
            'user': {
                'id':        str(officer.id),
                'pno':       officer.pno,
                'role':      officer.role,
                'is_active': officer.is_active,
                'must_change_password': officer.must_change_password,
                'profile': {
                    'name': profile.name,
                    'rank': { 'code': profile.rank.code, 'name': profile.rank.name, 'level': profile.rank.level },
                    'unit': { 'code': officer.unit.code, 'name': officer.unit.name }
                }
            }
        })


class RefreshTokenView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        refresh_token_str = request.data.get('refresh_token', '')
        request_device_id = request.data.get('device_id', '')

        try:
            refresh = RefreshToken(refresh_token_str)
            token_device_id = refresh.get('device_id', '')

            if token_device_id != request_device_id:
                return Response({'status': 'error', 'message': 'device_mismatch'},
                                status=status.HTTP_403_FORBIDDEN)

            new_access  = str(refresh.access_token)
            refresh.blacklist()
            new_refresh = RefreshToken.for_user(
                Officer.objects.get(id=refresh['user_id'])
            )
            new_refresh['device_id'] = request_device_id

            return Response({
                'access_token':  new_access,
                'refresh_token': str(new_refresh),
                'expires_in':    3600
            })
        except Exception:
            return Response({'status': 'error', 'message': 'Token अमान्य'},
                            status=status.HTTP_401_UNAUTHORIZED)


def _require_role(user, *allowed_roles):
    return user.role in allowed_roles


class CheckUserRoleView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        pno = request.data.get('pno', '').strip()
        if not pno:
            return Response({'status': 'error', 'message': 'PNO आवश्यक है'},
                            status=status.HTTP_400_BAD_REQUEST)
        try:
            officer = Officer.objects.get(pno=pno)
        except Officer.DoesNotExist:
            return Response({'status': 'error', 'message': 'PNO नहीं मिला'},
                            status=status.HTTP_404_NOT_FOUND)

        if not officer.is_active or officer.is_blocked:
            return Response({'status': 'error', 'message': 'खाता निष्क्रिय है'},
                            status=status.HTTP_403_FORBIDDEN)

        profile = officer.profile
        return Response({
            'status': 'success',
            'role': officer.role,
            'name': profile.name,
        })


class DeviceBindingPermission(BasePermission):
    """
    Hardened: Rejects requests if X-Device-Id doesn't match the registered device_id.
    Prevents token-theft-replay from different devices.
    """
    def has_permission(self, request, view):
        if not request.user or not request.user.is_authenticated:
            return False
        
        if not request.user.device_id:
            return True
            
        header_device_id = request.headers.get('X-Device-Id')
        if not header_device_id:
            return False
            
        return request.user.device_id == header_device_id


class ProfileView(APIView):
    permission_classes = [IsAuthenticated]
    
    def get(self, request):
        try:
            officer = request.user
            profile = getattr(officer, 'profile', None)
            
            # Mission-Grade Logging: Record every profile access for audit
            # Suspicious if token is valid but data is missing
            if not profile:
                import logging
                logger = logging.getLogger('kavach')
                logger.error(f"PROFILE_DATA_MISSING: PNO={officer.pno} | ID={officer.id}")
            
            return Response({
                "status": "success",
                "data": {
                    "id": str(officer.id),
                    "pno": officer.pno,
                    "role": officer.role,
                    "is_active": officer.is_active,
                    "must_change_password": officer.must_change_password,
                    "profile": {
                        "name": profile.name if profile else "Unknown",
                        "rank": { "code": profile.rank.code, "name": profile.rank.name, "level": profile.rank.level } if profile and profile.rank else None,
                        "unit": { "code": officer.unit.code, "name": officer.unit.name } if officer.unit else None
                    }
                }
            })
        except Exception as e:
            import traceback
            import logging
            logger = logging.getLogger('kavach')
            error_trace = traceback.format_exc()
            logger.critical(f"PROFILE_VIEW_FAILURE: User={request.user} | Error={str(e)}\n{error_trace}")
            return Response({
                "status": "error",
                "message": "Internal Operational Error during profile sync.",
                "trace": error_trace if settings.DEBUG else None
            }, status=500)


class ChangePasswordView(APIView):
    permission_classes = [IsAuthenticated, DeviceBindingPermission]
    def post(self, request):
        from .services.auth_service import AuthService
        success, message = AuthService.change_password(
            request.user,
            request.data.get('old_password', ''),
            request.data.get('new_password', ''),
            request.data.get('device_id', 'unknown')
        )
        if success:
            return Response({'status': 'success', 'message': message})
        return Response({'status': 'error', 'message': message}, status=400)

class RegisterFcmTokenView(APIView):
    """
    POST /api/auth/register-fcm
    { token }
    Saves the Firebase Messaging token for the authenticated user.
    """
    def post(self, request):
        token = request.data.get('token')
        if not token:
            return Response({'status': 'error', 'message': 'Token required'}, status=400)
        
        request.user.fcm_token = token
        request.user.save(update_fields=['fcm_token'])
        
        _audit(request.user, 'FCM_TOKEN_REGISTERED', request)
        return Response({'status': 'success', 'message': 'FCM Token registered'})


class LogoutView(APIView):
    """
    POST /api/auth/logout
    { refresh_token }
    Blacklists the refresh token and clears device FCM token.
    """
    permission_classes = [IsAuthenticated]
    def post(self, request):
        refresh_token = request.data.get('refresh_token')
        if not refresh_token:
            return Response({"error": "refresh_token is required"}, status=400)
            
        try:
            token = RefreshToken(refresh_token)
            token.blacklist()
            
            # Clear FCM on logout
            request.user.fcm_token = None
            request.user.save(update_fields=['fcm_token'])
            
            _audit(request.user, 'LOGOUT', request)
            return Response({"status": "logged_out"})
        except Exception:
            return Response({"error": "Invalid token"}, status=400)


# ── OTA / App Version Endpoints ────────────────────────────────────────────────
# PILOT PHASE: Static responses only — zero DB, zero imports, zero crash risk.
# These are called at app startup before login; a single crash = "System Halted".

class AppVersionView(APIView):
    """
    GET /api/v1/app-version/?version_code=<int>
    Pilot: always returns no_update. Expand logic after startup is stable.
    """
    permission_classes = [AllowAny]

    def get(self, request):
        return Response({
            'version_code':          1,
            'min_supported_version': 1,
            'force_update':          False,
            'is_rollback':           False,
            'is_critical':           False,
            'channel':               'stable',
            'release_notes':         'KAVACH pilot release.',
            'download_url':          '',
            'schema_version':        1,
        })


class AppUpdateLogView(APIView):
    """
    POST /api/v1/app-update/log/
    Pilot: silently accepts and discards. No DB, no logging calls.
    """
    permission_classes = [AllowAny]

    def post(self, request):
        return Response({'status': 'logged'})
