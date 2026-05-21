import time
import json
import hashlib
import hmac
from django.conf import settings
from django.http import HttpResponseForbidden
from django.utils.deprecation import MiddlewareMixin
from django.core.cache import cache
from django.utils import timezone
from django.urls import resolve, Resolver404
from apps.auth_app.models import OfficerActivity as AuditLog

# ── ISSUE 6 + Risk 3 FIX: Sensitive Route Matching ───────────────────────────
#
# Old approach (startswith — bypassable):
#   any(request.path.startswith(p) for p in PATTERNS)
#   Problem: /adminish/ and /admin-export/ both match /admin/
#
# New approach (Django URL resolver):
#   resolve(request.path).url_name in SENSITIVE_URL_NAMES
#   Problem: resolver gives exact named URL match — no prefix ambiguity
#
# Risk 8 FIX — Risk Engine Persistence Strategy:
#   Transient (cache only): rate counters, session risk scores, hourly metrics
#   Persistent (DB): threat reputation (OfficerCredential.security_risk_score),
#                    device trust score (OfficerDevice.trust_score),
#                    long-term suspicion (OfficerCredential.permanent_suspicion)
#   The models already have these DB fields — they survive restarts.
#   Cache counters are acceptable to lose on restart (they rebuild quickly).
#
# Sensitive URL names — matched by Django's named URL registry (exact match).
# Add the url name (as defined in urls.py) for any protected endpoint.
SENSITIVE_URL_NAMES = {
    'integrity-nonce',    # /integrity/nonce/ — ironically exempt from integrity gate itself
    'integrity-verify',
    'integrity-metrics',
    'change-password',
    'logout',
    'register-fcm',
    'notification-ack',
    # Add new sensitive endpoint url names here
}

# Fallback prefix list — used ONLY if resolve() raises Resolver404
# (e.g. for pass-through paths that aren't in Django URLconf)
try:
    from apps.auth_app.integrity_decorators import SENSITIVE_ROUTE_PATTERNS as _FALLBACK_PATTERNS
except ImportError:
    _FALLBACK_PATTERNS = []


def _is_sensitive_route(path: str) -> bool:
    """
    Risk 3 FIX: Use Django URL resolver for exact route matching.
    Falls back to prefix matching only if resolver fails (e.g. /admin/ Django admin).

    This prevents bypass via /adminish/, /bulk_fake/, /export-data/ etc.
    """
    try:
        match = resolve(path)
        # Named URL match — exact, unambiguous
        if match.url_name in SENSITIVE_URL_NAMES:
            return True
        # Namespace match (e.g. all URLs under 'admin:' namespace)
        if match.namespace in ('admin',):
            return True
    except Resolver404:
        pass
    # Fallback: prefix matching for paths outside URLconf (Django admin, etc.)
    return any(path.startswith(p) for p in _FALLBACK_PATTERNS)


class RequestIntegrityMiddleware(MiddlewareMixin):
    """
    Hardened Request Integrity Middleware with Failure Logging.
    """
    def log_failure(self, request, reason):
        # Log to AuditLog so Admin can see attack attempts
        pno = "ANONYMOUS"
        if request.user.is_authenticated:
            pno = getattr(request.user, 'pno', str(request.user.id))
            
        AuditLog.objects.create(
            pno=pno,
            officer=request.user if request.user.is_authenticated else None,
            unit=request.user.unit if (request.user and request.user.is_authenticated) else None,
            action="SECURITY_FAILURE",
            route=request.path,
            result="BLOCKED",
            severity="SECURITY",
            ip_address=self.get_client_ip(request),
            metadata={
                "reason": reason,
                "method": request.method,
                "correlation_id": getattr(request, 'correlation_id', 'unknown'),
            }
        )

    def update_metrics(self, category, value=1):
        """Update real-time operational metrics for the dashboard."""
        key = f"metrics_{category}_{timezone.now().strftime('%Y%m%d_%H')}"
        try:
            cache.incr(key, value)
        except ValueError:
            cache.set(key, value, timeout=86400)

    def get_signed_config(self, key, default):
        """Fetch config with Emergency Override & Version/Delay verification."""
        config_data = cache.get("kavach_remote_config", {})
        payload = config_data.get('payload', {})
        version = config_data.get('version', 0)
        effective_at = config_data.get('effective_at', 0)
        is_emergency = config_data.get('is_emergency', False)
        
        # 1. Emergency Signed Path (Instant)
        if is_emergency:
            return payload.get(key, getattr(settings, key, default))

        # 2. Normal Change Delay Window
        if effective_at > time.time():
            return getattr(settings, key, default)

        active_payload = payload if version >= cache.get("kavach_config_version_lock", 0) else {}
        return active_payload.get(key, getattr(settings, key, default))

    def process_request(self, request):
        import uuid
        request.correlation_id = str(uuid.uuid4())
        request.app_version = request.headers.get('X-App-Version', 'unknown')
        request.device_oem = request.headers.get('X-Device-Manufacturer', 'unknown')
        request.device_model = request.headers.get('X-Device-Model', 'unknown')
        request.android_sdk = request.headers.get('X-Android-Version', 'unknown')

    def process_view(self, request, view_func, view_args, view_kwargs):
        # Operational Layer: Deterministic Canary (5% Stable Bucketing)
        is_canary = False
        if request.user.is_authenticated and not request.user.is_staff:
            user_hash = int(hashlib.md5(str(request.user.pno).encode()).hexdigest(), 16)
            is_canary = (user_hash % 100 < 5) 

        # 1. Gated Multi-Signal Auto-Response (Feedback Loop)
        system_stress = cache.get("global_system_stress", 0)
        hmac_failures = cache.get(f"metrics_hmac_failures_{timezone.now().strftime('%Y%m%d_%H')}", 0)
        
        # Anti-Flapping Cooldown (15 min)
        last_esc = cache.get("last_security_escalation", 0)
        cooldown_active = (time.time() - last_esc) < 900
        
        AUTO_MINIMAL_LEVEL = 0
        if not cooldown_active:
            # Gated: Stress > 80 AND HMAC Failures > 50 -> L3
            if system_stress > 80 and hmac_failures > 50:
                AUTO_MINIMAL_LEVEL = 3
                cache.set("last_security_escalation", time.time(), timeout=1800)
            elif hmac_failures > 30: 
                AUTO_MINIMAL_LEVEL = 2
                cache.set("last_security_escalation", time.time(), timeout=1800)
        
        MINIMAL_LEVEL = max(AUTO_MINIMAL_LEVEL, self.get_signed_config('KAVACH_MINIMAL_LEVEL', 0))

        if request.method in ['POST', 'PUT', 'PATCH']:
            # Allow Auth endpoints to bypass integrity check (they don't have tokens/secrets yet)
            if any(path in request.path for path in ['/api/login', '/api/v1/login', '/api/otp']):
                return None

            # ── Integrity Trust Window Check (Level-Based) ────────────────────
            # First pass: middleware enforces time windows + FAILED blocks
            # Second pass: endpoint decorators enforce level-specific gates
            _INTEGRITY_EXEMPT = [
                '/api/v1/auth/integrity/nonce/',
                '/api/v1/auth/integrity/verify/',
                '/api/v1/auth/logout/',
                '/api/v1/login/',
                '/api/v1/verify-otp/',
            ]
            # Level-based trust windows (milliseconds)
            _LEVEL_TRUST_WINDOWS_MS = {
                'STRONG':   30 * 60 * 1000,
                'DEVICE':   10 * 60 * 1000,
                'BASIC':     2 * 60 * 1000,
                'DEGRADED':  5 * 60 * 1000,
                'FAILED':    0,
            }
            is_integrity_exempt = any(request.path.startswith(p) for p in _INTEGRITY_EXEMPT)

            # Risk 3 FIX: Use URL resolver instead of startswith() for sensitive route detection.
            # _is_sensitive_route() uses Django's resolve() for exact named-URL matching.
            # Fallback to prefix matching only if resolve() fails (e.g. /admin/).
            is_sensitive_route = _is_sensitive_route(request.path)

            if (
                not is_integrity_exempt
                and request.user.is_authenticated
                and not _is_django_web_admin(request)
            ):
                from django.http import JsonResponse as _JR
                integrity_level  = request.headers.get('X-Integrity-Level', '')
                attested_at_str  = request.headers.get('X-Attested-At', '0')
                try:
                    attested_at_ms = int(attested_at_str)
                except (ValueError, TypeError):
                    attested_at_ms = 0

                now_ms = int(time.time() * 1000)
                cached_expires_at = cache.get(f"integrity_expires_at_{request.user.id}")
                if cached_expires_at is not None:
                    is_expired = now_ms > cached_expires_at
                else:
                    window_ms  = _LEVEL_TRUST_WINDOWS_MS.get(integrity_level, 0)
                    age_ms     = now_ms - attested_at_ms
                    is_expired = bool(integrity_level and attested_at_ms > 0 and window_ms > 0 and age_ms > window_ms)

                from dataclasses import dataclass
                
                # Future Evolution: IntegrityContext
                # We attach this context to the request so decorators and policy engines
                # can make fine-grained authorization decisions without parsing headers again.
                # Risk Fix: Immutable security context to prevent accidental runtime mutations.
                @dataclass(frozen=True)
                class IntegrityContext:
                    level: str
                    is_expired: bool
                    age_ms: int
                    fresh_minutes: int
                        
                request.integrity = IntegrityContext(
                    level=integrity_level,
                    is_expired=is_expired,
                    age_ms=age_ms if 'age_ms' in locals() else now_ms - attested_at_ms,
                    fresh_minutes=(age_ms if 'age_ms' in locals() else now_ms - attested_at_ms) // 60000 if (age_ms if 'age_ms' in locals() else now_ms - attested_at_ms) > 0 else 0
                )

                # Middleware retains ONLY the absolute baseline security (Gate 1)
                is_pilot_mode = getattr(settings, 'KAVACH_PILOT_MODE', False)

                if integrity_level == 'FAILED':
                    if not is_pilot_mode:
                        self.log_failure(request, 'INTEGRITY_FAILED_DEVICE_ACCESS_ATTEMPT')
                        return _JR({
                            'error': 'Device integrity check failed. Access denied.',
                            'code':  'KAVACH_INTEGRITY_FAILED'
                        }, status=403)
                    else:
                        self.log_failure(request, 'PILOT_MODE_BYPASS_TRIGGERED_FOR_FAILED')

                if integrity_level == 'BASIC':
                    if not is_pilot_mode:
                        self.log_failure(request, 'INTEGRITY_BASIC_LIVE_READ_ATTEMPT')
                        return _JR({
                            'error': 'BASIC integrity cannot access live data. Use locally cached data.',
                            'code':  'KAVACH_INTEGRITY_BASIC_NO_LIVE_READS',
                            'no_live_reads': True,
                            'current':  'BASIC',
                            'required': 'DEVICE'
                        }, status=403)
                    else:
                        self.log_failure(request, 'PILOT_MODE_BYPASS_TRIGGERED_FOR_BASIC')

                if is_expired:
                    if not is_pilot_mode:
                        return _JR({
                            'error': f'Device attestation expired ({integrity_level} level). Re-verify.',
                            'code':  'KAVACH_INTEGRITY_EXPIRED',
                            'level': integrity_level
                        }, status=401)
                    else:
                        self.log_failure(request, 'PILOT_MODE_BYPASS_TRIGGERED_FOR_EXPIRED')

                if is_sensitive_route and not integrity_level:
                    if not is_pilot_mode:
                        return _JR({
                            'error': 'This endpoint requires device attestation.',
                            'code':  'KAVACH_INTEGRITY_MISSING',
                            'hint':  'Call /integrity/verify/ first.'
                        }, status=401)
                    else:
                        self.log_failure(request, 'PILOT_MODE_BYPASS_TRIGGERED_FOR_MISSING')
            else:
                from dataclasses import dataclass
                # Default empty context for exempt routes
                @dataclass(frozen=True)
                class IntegrityContext:
                    level: str = ''
                    is_expired: bool = False
                    age_ms: int = 0
                    fresh_minutes: int = 0
                request.integrity = IntegrityContext()

            timestamp = request.headers.get('X-Timestamp')
            nonce = request.headers.get('X-Nonce')

            from django.http import JsonResponse
            # Layer 0: Integrity
            if not all([timestamp, nonce]):
                if getattr(settings, 'KAVACH_PILOT_MODE', False):
                    self.log_failure(request, "PILOT_BYPASS_MISSING_CONTEXT")
                    return None
                return JsonResponse({"error": "Security context missing", "code": "KAVACH_INTEGRITY_FAILURE"}, status=401)

            signature = request.headers.get('X-Kavach-Signature')
            # Secret key from Credentials
            secret = request.user.credentials.device_secret if (request.user.is_authenticated and hasattr(request.user, 'credentials')) else settings.SECRET_KEY 
            expected_data = f"{nonce}{timestamp}{request.method}{request.path}"
            expected_sig = hmac.new(secret.encode(), expected_data.encode(), hashlib.sha256).hexdigest()

            if not signature or not hmac.compare_digest(signature, expected_sig):
                self.update_metrics("hmac_failures")
                self.log_failure(request, f"HMAC_MISMATCH (Expected: {expected_sig[:8]}... Sent: {signature[:8] if signature else 'None'}...)")
                
                if getattr(settings, 'KAVACH_PILOT_MODE', False):
                    # Pilot override: log it but don't block. 
                    # This prevents lockout due to secret key mismatch.
                    return None
                    
                return JsonResponse({"error": "Request integrity check failed", "code": "KAVACH_INTEGRITY_FAILURE"}, status=401)

            # Layer 1: Responses
            if MINIMAL_LEVEL >= 2: 
                try:
                    ts = int(timestamp)
                    if request.user.is_authenticated and hasattr(request.user, 'credentials'):
                        if ts < (request.user.credentials.last_request_at - 1): return HttpResponseForbidden("Sequencing error")
                except: return HttpResponseForbidden("Invalid context")

            if MINIMAL_LEVEL >= 3: 
                ip = self.get_client_ip(request)
                if cache.get(f"strict_throttle_{ip}"): return HttpResponseForbidden("Emergency protocol active")
                cache.set(f"strict_throttle_{ip}", True, timeout=15)

            if MINIMAL_LEVEL > 0:
                if request.user.is_authenticated:
                    cred = request.user.credentials
                    cred.last_request_at = max(int(timestamp), cred.last_request_at)
                    cred.save(update_fields=['last_request_at'])
                return None

            # 2. Adaptive Intelligence (Canary + Trusted-Only Relaxation)
            USE_CLUSTERING = self.get_signed_config('KAVACH_ENABLE_CLUSTERING', True)
            trusted_challenges = cache.get(f"metrics_trusted_challenges_{timezone.now().strftime('%Y%m%d_%H')}", 0)
            is_fp_storm = trusted_challenges > 150 # Relax only if TRUSTED users are being hit
            
            cluster_risk = 0.0
            if USE_CLUSTERING and not is_fp_storm:
                normalized_path = request.path.rstrip('/')
                pattern_hash = f"pat_{hashlib.md5(f'{request.method}{normalized_path}'.encode()).hexdigest()[:8]}"
                pattern_data = cache.get(pattern_hash, {"count": 0, "anomalies": 0})
                pattern_data['count'] += 1
                
                baseline_rate = cache.get("global_anomaly_rate", 0.05)
                current_rate = pattern_data['anomalies'] / max(1, pattern_data['count'])
                
                multiplier = 1.5 if is_canary else 2.0
                if pattern_data['count'] > 50 and current_rate > (baseline_rate * multiplier):
                    cluster_risk = 35.0 
                    self.update_metrics("cluster_triggers")
                cache.set(pattern_hash, pattern_data, timeout=60)

            # 3. Intelligent Risk Evaluation
            user_risk = request.user.credentials.security_risk_score if (request.user.is_authenticated and hasattr(request.user, 'credentials')) else 0
            total_risk = (user_risk * 0.8) + cluster_risk if request.user.is_authenticated else (user_risk * 0.4) + cluster_risk
            
            # 4. Adaptive Challenges & Weighted Metrics
            challenge_threshold = max(30.0, 70.0 - (request.user.credentials.silent_risk_score / 4.0 if hasattr(request.user, 'credentials') else 0))
            
            if total_risk >= challenge_threshold:
                self.update_metrics("challenges_issued")
                # Weighting: Only count as trusted challenge if risk is low
                if request.user.is_authenticated and request.user.credentials.security_risk_score < 20:
                    self.update_metrics("trusted_challenges")

                challenges_key = f"challenges_24h_{request.user.pno}" if request.user.is_authenticated else f"challenges_ip_24h_{self.get_client_ip(request)}"
                recent_challenges = cache.get(challenges_key, 0)
                cache.set(challenges_key, recent_challenges + 1, timeout=86400)

                challenge_type = "OTP_VERIFY"
                user_msg = "Security handshake required."
                if recent_challenges > 3: challenge_type = "DEVICE_REBIND"
                
                cache.set("global_system_stress", system_stress + 5, timeout=300)
                from django.http import JsonResponse
                return JsonResponse({"status": "challenge", "action": challenge_type, "message": user_msg}, status=401)
            
            if request.user.is_authenticated:
                cred = request.user.credentials
                cred.save(update_fields=['last_request_at', 'security_risk_score', 'silent_risk_score'])

        return None

    def process_response(self, request, response):
        """Elite Layer: Capped Trust Rewards."""
        if hasattr(request, 'user') and request.user.is_authenticated:
            # Decay Capping: Max 0.5 reduction per hour
            decay_key = f"decay_1h_{request.user.pno}"
            current_decay = cache.get(decay_key, 0.0)
            
            if current_decay < 0.5:
                reward = 0.0
                # Reward ONLY on validated critical successes (like OTP verify)
                if getattr(request, 'validated_success', False) and response.status_code < 400:
                    reward = 0.1
                
                if reward > 0:
                    cred = request.user.credentials
                    cred.security_risk_score = max(0.0, cred.security_risk_score - reward)
                    cred.save(update_fields=['security_risk_score'])
                    cache.set(decay_key, current_decay + reward, timeout=3600)
        return response

    def get_client_ip(self, request):
        x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
        if x_forwarded_for:
            return x_forwarded_for.split(',')[0]
        return request.META.get('REMOTE_ADDR')

    def flag_security_anomaly(self, request):
        """Increments risk score and updates global stress."""
        ip = self.get_client_ip(request)
        ip_risk_key = f"ip_risk_{ip}"
        
        # IP Risk
        current_ip_risk = cache.get(ip_risk_key, 0)
        cache.set(ip_risk_key, current_ip_risk + 5, timeout=3600) 
        
        # Global Stress
        system_stress = cache.get("global_system_stress", 0)
        cache.set("global_system_stress", system_stress + 2, timeout=300)
        
        # User Risk
        if request.user.is_authenticated and hasattr(request.user, 'credentials'):
            cred = request.user.credentials
            cred.security_risk_score += 2.0
            cred.save(update_fields=['security_risk_score'])
