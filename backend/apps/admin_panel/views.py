from django.utils import timezone
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated, AllowAny
from apps.auth_app.permissions import IsAdminRole

from apps.auth_app.models import Officer, OfficerActivity as AuditLog, OfficerProfile
from apps.admin_panel.models import AppRelease
from apps.training.models import TrainingSession, Training, TrainingAssignment
from apps.behavior.models import DisciplineScore, BehaviorEvent
from apps.orders.models import Order, Alert
from apps.auth_app.orchestration.event_bus import EventBus


def can_assign_role(current_user, target_role):
    """
    SUPERUSER -> can assign anything
    ADMIN     -> can only assign USER
    USER      -> nothing
    """
    if current_user.role == "SUPERUSER":
        return True
    if current_user.role == "ADMIN" and target_role == "USER":
        return True
    return False


class AdminUserListView(APIView):
    """
    GET /api/admin/users
    Returns all officers with compliance status + discipline score.
    Admin dashboard main view.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        if request.user.role == "SUPERUSER":
            officers = Officer.objects.filter(is_staff=False).select_related('discipline_score')
        else:
            # Hierarchy-based filtering (Hardened)
            # Admin only sees people in their direct chain of command
            officer_ids = [o.id for o in request.user.get_subordinates()]
            officers = Officer.objects.filter(id__in=officer_ids, is_staff=False).select_related('discipline_score')
        data = []
        for o in officers:
            try:
                ds = o.discipline_score
                ds_score = ds.score
                ds_grade = ds.grade
                requires_review = ds.requires_review
                risk_level = ds.risk_level
                trust_level = ds.trust_level
                anomaly_reasons = ds.anomaly_reasons
                predicted_score = ds.predicted_score
                trajectory = ds.trajectory_status
                last_activity = ds.last_activity_at.isoformat() if ds.last_activity_at else None
            except DisciplineScore.DoesNotExist:
                ds_score, ds_grade, requires_review = 100, 'EXCELLENT', False
                risk_level, trust_level = 'NORMAL', 'NORMAL'
                anomaly_reasons, predicted_score, trajectory = [], 100, 'STABLE'
                last_activity = None

            training_count   = TrainingSession.objects.filter(officer=o, status='COMPLETED').count()
            suspicious_count = TrainingSession.objects.filter(officer=o, is_suspicious=True).count()

            # High Fidelity Deep Insights
            expl = {}
            if hasattr(o, 'discipline_score'):
                 expl = getattr(o.discipline_score, 'last_decision_explanation', {})

            profile = o.profile
            data.append({
                'pno':              o.pno,
                'name':             profile.name,
                'rank':             profile.rank.code,
                'unit':             o.unit.code,
                'is_blocked':       not o.is_active or profile.service_status == 'SUSPENDED',
                'discipline_score': ds_score,
                'grade':            ds_grade,
                'risk_level':       risk_level,
                'trust_level':      trust_level,
                'anomaly_reasons':  anomaly_reasons,
                'predicted_score':  predicted_score,
                'trajectory':       trajectory,
                'last_activity':    last_activity,
                
                # New "Elite" Fields
                "confidence_score": expl.get('confidence', 'N/A'),
                "signal_weights":   expl.get('decision_path', {}),
                "evidence_preview": expl.get('raw_evidence', {}),
                
                'requires_review':  requires_review,
                'trainings_done':   training_count,
                'suspicious_sessions': suspicious_count,
            })
        return Response({'status': 'success', 'data': data})


class AdminUserActionView(APIView):
    """
    POST /api/admin/user/action
    { pno, action, training_id?, reason }

    Actions:
      BLOCK         — block officer login
      UNBLOCK       — re-enable
      RESET_TRAINING — reset specific training (officer must redo)
      RESET_DEVICE  — clear device binding (officer can login from new device)
      FORCE_FLAG    — manually mark officer as flagged
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        pno         = request.data.get('pno', '')
        action      = request.data.get('action', '')
        training_id = request.data.get('training_id')
        reason      = request.data.get('reason', '')

        try:
            officer = Officer.objects.get(pno=pno)
        except Officer.DoesNotExist:
            return Response({'status': 'error', 'message': 'Officer नहीं मिला'}, status=404)

        if action == 'BLOCK':
            officer.is_active = False
            officer.save(update_fields=['is_active'])
            profile = officer.profile
            profile.service_status = 'SUSPENDED'
            profile.save(update_fields=['service_status'])
            
            # REALTIME DISPATCH (Write-Path Consistency)
            EventBus.publish(
                'GovernanceApplied',
                request.user,
                officer.unit,
                {'type': 'SESSION_REVOKED', 'target_pno': pno, 'reason': reason, 'priority': 'CRITICAL'},
                request.META.get('HTTP_X_TRACE_ID', 'ADMIN_ACTION_' + pno)
            )
            msg = f"{pno} को block किया गया"

        elif action == 'UNBLOCK':
            officer.is_active = True
            officer.save(update_fields=['is_active'])
            profile = officer.profile
            profile.service_status = 'ACTIVE'
            profile.save(update_fields=['service_status'])
            
            # REALTIME DISPATCH
            EventBus.publish(
                'GovernanceApplied',
                request.user,
                officer.unit,
                {'type': 'USER_UPDATED', 'target_pno': pno, 'status': 'ACTIVE', 'priority': 'IMPORTANT'},
                request.META.get('HTTP_X_TRACE_ID', 'ADMIN_ACTION_' + pno)
            )
            msg = f"{pno} को unblock किया गया"

        elif action == 'RESET_TRAINING':
            if not training_id:
                return Response({'status': 'error', 'message': 'training_id आवश्यक है'}, status=400)
            TrainingSession.objects.filter(officer=officer, training_id=training_id).update(
                status='PENDING', quiz_score=None, quiz_passed=None,
                quiz_attempts=0, completed_at=None
            )
            msg = f"{pno} की training reset की गई"

        elif action == 'RESET_DEVICE':
            # In v3, we deactivate devices instead of clearing a single field on Officer
            officer.devices.all().update(status='REVOKED')
            msg = f"{pno} के सभी devices reset/revoke किए गए"

        elif action == 'EDIT_ROLE':
            new_role = request.data.get('role')
            if not new_role:
                return Response({'status': 'error', 'message': 'role आवश्यक है'}, status=400)
            
            if not can_assign_role(request.user, new_role):
                return Response({'status': 'error', 'message': 'Permission denied: Role assign rules violation'}, status=403)
            
            officer.role = new_role
            # If making someone ADMIN/SUPERUSER, they need is_staff
            if new_role in ['ADMIN', 'SUPERUSER']:
                officer.is_staff = True
            else:
                officer.is_staff = False
            officer.save()
            msg = f"{pno} का role {new_role} किया गया"

        else:
            return Response({'status': 'error', 'message': 'Unknown action'}, status=400)

        # Audit log
        AuditLog.objects.create(
            officer    = request.user,
            actor      = request.user,
            unit       = request.user.unit,
            pno        = request.user.pno,
            action     = f'ADMIN_{action}',
            severity   = 'WARNING',
            ip_address = request.META.get('REMOTE_ADDR'),
            metadata   = {'target_pno': pno, 'reason': reason}
        )

        return Response({'status': 'success', 'message': msg})


class AdminAssignTrainingView(APIView):
    """
    POST /api/admin/assign-training
    { training_id, pno_list, deadline?, mandatory }

    Assigns training to specific officers with optional deadline.
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        training_id = request.data.get('training_id')
        pno_list    = request.data.get('pno_list', [])
        deadline    = request.data.get('deadline')
        mandatory   = request.data.get('mandatory', True)

        try:
            training = Training.objects.get(id=training_id)
        except Training.DoesNotExist:
            return Response({'status': 'error', 'message': 'Training नहीं मिली'}, status=404)

        assigned, skipped = 0, 0
        for pno in pno_list:
            try:
                officer = Officer.objects.get(pno=pno)
                TrainingAssignment.objects.get_or_create(
                    training   = training,
                    officer    = officer,
                    defaults   = {
                        'deadline':    deadline,
                        'assigned_by': request.user
                    }
                )
                assigned += 1
            except Officer.DoesNotExist:
                skipped += 1

        return Response({
            'status':   'success',
            'assigned': assigned,
            'skipped':  skipped
        })


class AdminSuspiciousUsersView(APIView):
    """GET /api/admin/suspicious — lists all suspicious sessions for review."""
    permission_classes = [IsAdminRole]

    def get(self, request):
        sessions = TrainingSession.objects.filter(
            is_suspicious=True
        ).select_related('officer', 'training').order_by('-completed_at')

        data = [{
            'pno':              s.officer.pno,
            'name':             s.officer.profile.name,
            'training':         s.training.title,
            'suspicious_reason':s.suspicious_reason,
            'quiz_score':       s.quiz_score,
            'heartbeat_count':  s.heartbeat_count,
            'completed_at':     s.completed_at,
        } for s in sessions]

        return Response({'status': 'success', 'data': data})


class AdminUserCreateView(APIView):
    """
    POST /api/admin/users/create
    { pno, name, unit, role, email, rank? }
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        pno   = request.data.get('pno')
        name  = request.data.get('name')
        unit  = request.data.get('unit')
        role  = request.data.get('role', 'USER')
        email = request.data.get('email')
        rank  = request.data.get('rank', 'Constable')

        if not all([pno, name, unit, role]):
            return Response({'status': 'error', 'message': 'Missing required fields'}, status=400)

        if not can_assign_role(request.user, role):
            return Response({'status': 'error', 'message': 'Permission denied for this role'}, status=403)

        if Officer.objects.filter(pno=pno).exists():
            return Response({'status': 'error', 'message': 'PNO already exists'}, status=400)

        is_staff = True if role in ['ADMIN', 'SUPERUSER'] else False
        
        officer = Officer.objects.create_user(
            pno=pno,
            unit=unit, # unit should be a UnitMaster instance or ID depending on logic
            role=role
        )
        # Create Profile
        OfficerProfile.objects.create(
            officer=officer,
            name=name,
            unit=unit, # redundant as per v3 design
            rank=rank, # RankMaster instance
            email=email
        )

        # Audit
        AuditLog.objects.create(
            officer=request.user,
            actor=request.user,
            unit=request.user.unit,
            pno=request.user.pno,
            action='ADMIN_CREATE_USER',
            severity='INFO',
            metadata={'target_pno': pno, 'role': role}
        )

        return Response({'status': 'success', 'message': f'User {pno} created successfully'})


class AdminOrderCreateView(APIView):
    """
    POST /api/admin/orders/create
    { title, content, priority (mandatory?), unit }
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        title        = request.data.get('title')
        content      = request.data.get('content')
        is_mandatory = request.data.get('priority') == 'CRITICAL' # or just pass bool
        unit         = request.data.get('unit')

        order = Order.objects.create(
            title=title,
            content=content,
            is_mandatory=is_mandatory,
            issued_by=request.user.name
        )
        
        # Note: Current Order model doesn't have target_unit, 
        # it seems orders are global in the current model.
        # But we can store metadata or filter later if needed.

        return Response({'status': 'success', 'order_id': str(order.id)})


class AdminAlertCreateView(APIView):
    """
    POST /api/admin/alerts/create
    { title, content, type, target_unit }
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        title       = request.data.get('title')
        content     = request.data.get('content')
        alert_type  = request.data.get('type', 'NORMAL')
        target_unit = request.data.get('target_unit')

        # Role check for CRITICAL alerts
        if alert_type == 'CRITICAL':
            if request.user.role not in ['SUPERUSER', 'ADMIN']:
                return Response({'status': 'error', 'message': 'Only Admins/Superusers can create Critical alerts'}, status=403)

        alert = Alert.objects.create(
            title=title,
            content=content,
            type=alert_type,
            target_unit=target_unit,
            created_by=request.user.name
        )

        return Response({'status': 'success', 'alert_id': str(alert.id)})


class AdminStatsView(APIView):
    """
    GET /api/admin/stats
    Returns counts for dashboard.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        total_officers = Officer.objects.count()
        critical_alerts = Alert.objects.filter(type='CRITICAL').count()
        # Mock compliance for now, or calculate if data exists
        compliance = "94%" 
        
        # Unit breakdown
        units = Officer.objects.values('unit').annotate(count=models.Count('id'))
        
        return Response({
            'status': 'success',
            'total_officers': total_officers,
            'critical_alerts': critical_alerts,
            'compliance': compliance,
            'units': list(units)
        })


class AdminReportView(APIView):
    """
    GET /api/admin/reports/export?type=unit
    Placeholder for PDF generation.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        report_type = request.query_params.get('type', 'general')
        # In real world, use ReportLab or WeasyPrint here
        return Response({
            'status': 'success', 
            'message': f'Report of type {report_type} is being generated.',
            'download_url': f'/media/reports/mock_{report_type}.pdf'
        })


class AdminAuditLogView(APIView):
    """
    GET /api/admin/audit-logs
    Returns all security actions.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        logs = AuditLog.objects.all().select_related('officer').order_by('-created_at')[:200]
        data = [{
            'id': l.id,
            'action': l.action,
            'admin_pno': l.pno,
            'admin_name': l.officer.profile.name if l.officer else 'System',
            'target_pno': l.metadata.get('target_pno', 'N/A'),
            'ip': l.ip_address,
            'timestamp': l.created_at,
            'details': l.metadata
        } for l in logs]
        
        return Response({'status': 'success', 'data': data})

from rest_framework.throttling import UserRateThrottle

class AppUpdateThrottle(UserRateThrottle):
    rate = '3/minute' # Strict limit for APK downloads

class AppVersionCheckView(APIView):
    """
    Returns latest approved version info for the user's channel.
    Hardened with SHA-256 hash and CO approval check.
    """
    permission_classes = [AllowAny]

    def get(self, request):
        import uuid
        import hashlib
        from django.core.cache import cache
        from django.db.models import Q
        
        device_id = request.headers.get('X-Device-Id', 'unknown')
        
        if request.user.is_authenticated:
            user_channel = request.user.app_channel
            pno_hash = int(hashlib.md5(request.user.pno.encode()).hexdigest(), 16) % 100
        else:
            # Fallback for startup anonymous check
            user_channel = 'PILOT'
            pno_hash = 0 # Include in all rollouts for anonymous start
            
        current_version = int(request.query_params.get('version_code', 0))
        
        # 2. Fetch Potential Releases (Emergency or Channel Match)
        # We order by -version_code to find the latest
        releases = AppRelease.objects.filter(
            Q(channel=user_channel) | Q(channel='EMERGENCY'),
            is_approved=True
        ).order_by('-version_code')
        
        selected_release = None
        for r in releases:
            # A. Emergency Expiry Check
            if r.channel == 'EMERGENCY' and r.expires_at and r.expires_at < timezone.now():
                continue
            
            # B. Staged Rollout Check (Bypassed if is_critical_override is True)
            if not r.is_critical_override and pno_hash >= r.rollout_percentage:
                continue
                
            selected_release = r
            break

        if not selected_release:
            return Response({"status": "no_update", "message": "You are on the latest version or not in current rollout wave."}, status=200)

        # 3. Rollback Logic
        # If user is on a version HIGHER than the latest approved version,
        # and the latest approved version has is_rollback_allowed=True,
        # we serve the 'previous_release' (if it exists) or just the latest stable.
        if current_version > selected_release.version_code:
            if selected_release.is_rollback_allowed:
                # Target is actually a downgrade
                pass # selected_release is already the one we want to serve
            else:
                # No rollback allowed, and user is ahead of us (maybe a dev build)
                return Response({"status": "no_update", "message": "User is on a newer version."}, status=200)

        # 4. Generate bound download token
        download_token = str(uuid.uuid4())
        token_data = {
            "pno": request.user.pno,
            "device_id": device_id,
            "release_id": selected_release.id
        }
        cache.set(f"dl_token_{download_token}", token_data, timeout=300) 
        
        return Response({
            "version_code": selected_release.version_code,
            "version_name": selected_release.version_name,
            "apk_hash": selected_release.apk_hash_sha256,
            "apk_size": selected_release.apk_file.size,
            "download_url": f"/api/admin/app-download/?token={download_token}",
            "force_update": selected_release.is_forced,
            "release_notes": selected_release.release_notes,
            "min_supported_version": selected_release.min_supported_version,
            "channel": selected_release.channel,
            "is_rollback": selected_release.is_rollback_allowed,
            "schema_version": selected_release.schema_version,
            "min_supported_schema": selected_release.min_supported_schema,
            "is_critical": selected_release.is_critical_override,
            "category": selected_release.release_category,
            "incident_id": selected_release.incident_id
        })

class ApproveAppReleaseView(APIView):
    """
    POST /api/admin/app-release/approve/
    { version_code, approved, reason, incident_id, category, blast_radius }
    """
    permission_classes = [IsAdminRole]

    def post(self, request):
        version_code = request.data.get('version_code')
        approved = request.data.get('approved', False)
        reason = request.data.get('reason', '')
        incident_id = request.data.get('incident_id', '')
        category = request.data.get('category', 'PLANNED')
        blast_radius = request.data.get('blast_radius', 'LOW')
        
        if request.user.profile.rank.code != 'CO' and request.user.role != 'COMMANDING_OFFICER':
             return Response({"error": "Only Commanding Officers or Superusers can approve releases."}, status=403)
        
        try:
            release = AppRelease.objects.get(version_code=version_code)
            
            # Enforce governance for critical/forced releases
            if approved and (release.is_critical_override or release.is_forced):
                if not all([reason, incident_id]):
                    return Response({"error": "Approval reason and Incident ID are mandatory for critical or forced updates."}, status=400)

            release.is_approved = approved
            if approved:
                release.approved_by = request.user
                release.approved_at = timezone.now()
                release.approval_reason = reason
                release.incident_id = incident_id
                release.release_category = category
                release.blast_radius = blast_radius
            release.save()
            
            AuditLog.objects.create(
                officer=request.user,
                actor=request.user,
                unit=request.user.unit,
                pno=request.user.pno,
                action="CRITICAL_RELEASE_PUBLISHED" if (approved and release.is_critical_override) else "RELEASE_APPROVAL",
                severity="SECURITY" if release.is_critical_override else "INFO",
                metadata={
                    "version_code": version_code, 
                    "approved": approved,
                }
            )
            return Response({"status": "success", "message": f"Release v{version_code} {'approved' if approved else 'unapproved'}."})
        except AppRelease.DoesNotExist:
            return Response({"error": "Release not found"}, status=404)

class AppDownloadView(APIView):
    """
    Serves the APK with strict token binding and single-use enforcement.
    """
    permission_classes = [AllowAny]
    throttle_classes = [AppUpdateThrottle]

    def get(self, request):
        from django.core.cache import cache
        from django.http import FileResponse, HttpResponseForbidden
        import os
        
        token = request.query_params.get('token')
        token_data = cache.get(f"dl_token_{token}")
        
        if not token_data:
            AuditLog.objects.create(
                action="INVALID_TOKEN_DOWNLOAD_ATTEMPT",
                unit=None, # System wide
                severity="SECURITY",
                metadata={"token": token, "ip": request.META.get('REMOTE_ADDR')}
            )
            return HttpResponseForbidden("Invalid, expired, or used download token.")
        
        # Identity and Release Binding
        device_id = request.headers.get('X-Device-Id')
        if device_id and device_id != token_data['device_id']:
            return HttpResponseForbidden("Token bound to a different device.")

        # 3. Enforce Single-Use
        cache.delete(f"dl_token_{token}")
        
        try:
            release = AppRelease.objects.get(id=token_data['release_id'])
            if not release.apk_file:
                return Response({"error": "APK file missing on server"}, status=404)
                
            AuditLog.objects.create(
                pno=token_data['pno'],
                unit=release.approved_by.unit if release.approved_by else None,
                action="APK_DOWNLOAD_SUCCESS",
                severity="INFO",
                metadata={"version_code": release.version_code, "device_id": token_data['device_id']}
            )
            
            return FileResponse(release.apk_file.open('rb'), as_attachment=True, filename=f"kavach_v{release.version_code}.apk")
        except AppRelease.DoesNotExist:
            return Response({"error": "Release not found"}, status=404)

class AppUpdateLogView(APIView):
    """
    Endpoint for app to report update events (Success, Hash Fail, Install Fail).
    """
    permission_classes = [AllowAny]

    def post(self, request):
        event = request.data.get('event')
        metadata = request.data.get('metadata', {})
        
        pno = request.user.pno if request.user.is_authenticated else "ANONYMOUS"
        
        AuditLog.objects.create(
            pno=pno,
            unit=request.user.unit if request.user.is_authenticated else None,
            action=f"APP_UPDATE_{event.upper()}",
            severity="INFO",
            metadata=metadata
        )
        return Response({"status": "logged"})

class SystemHealthView(APIView):
    """
    Returns real-time server health metrics (CPU, Memory, Disk).
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        import psutil
        from django.contrib.auth import get_user_model
        
        User = get_user_model()
        
        return Response({
            "cpu": psutil.cpu_percent(),
            "memory": psutil.virtual_memory().percent,
            "disk": psutil.disk_usage('/').percent,
            "active_users": User.objects.filter(is_active=True).count(),
            "status": "Healthy" if psutil.cpu_percent() < 80 else "Overloaded"
        })

class AdminDecisionDashboardView(APIView):
    """
    CO-Level Actionable Insights.
    Aggregates top defaulters, non-responsive users, and unit-wise discipline.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        from django.contrib.auth import get_user_model
        from django.db.models import Avg, Count, Q
        from datetime import timedelta
        from django.utils import timezone
        
        User = get_user_model()
        
        # 1. Top Defaulters (Lowest Discipline Scores)
        defaulters = User.objects.filter(role='USER').order_by('discipline_score__score')[:10]
        defaulters_data = [{
            "pno": u.pno,
            "name": u.profile.name,
            "unit": u.unit.code,
            "score": getattr(u.discipline_score, 'score', 100)
        } for u in defaulters]
        
        # 2. Non-Responsive Users (Un-acked notifications in last 24h)
        yesterday = timezone.now() - timedelta(days=1)
        non_responsive = NotificationLog.objects.filter(
            created_at__gt=yesterday,
            acked_at__isnull=True
        ).values('pno', 'officer__profile__name', 'officer__unit__code').annotate(
            unacked_count=Count('id')
        ).order_by('-unacked_count')[:10]
        
        # 3. Unit-wise Discipline Averages
        unit_stats = User.objects.values('unit').annotate(
            avg_score=Avg('discipline_score__score'),
            total_personnel=Count('id')
        ).order_by('-avg_score')

        return Response({
            "defaulters": defaulters_data,
            "non_responsive": non_responsive,
            "unit_stats": unit_stats,
            "summary": {
                "total_users": User.objects.count(),
                "critical_flagged": User.objects.filter(discipline_score__score__lt=50).count(),
                "avg_system_discipline": User.objects.aggregate(Avg('discipline_score__score'))['avg_score__avg'] or 100
            }
        })

class AdminLiveFeedView(APIView):
    """
    Real-time combined feed of Anomalies and Audit Actions.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        # 1. Recent Behavior Events (Anomalies)
        events = BehaviorEvent.objects.filter(
            event_type__in=['SEEK_ATTEMPT', 'QUIZ_TOO_FAST', 'DEVICE_MISMATCH', 'HEARTBEAT_GAP']
        ).select_related('officer').order_by('-received_at')[:30]
        
        feed = []
        for e in events:
            feed.append({
                'type': 'ANOMALY',
                'severity': 'HIGH' if e.event_type == 'DEVICE_MISMATCH' else 'MEDIUM',
                'title': e.get_event_type_display(),
                'pno': e.officer.pno,
                'name': e.officer.profile.name,
                'timestamp': e.received_at.isoformat(),
                'details': e.metadata
            })
            
        # 2. Recent Audit Logs
        logs = AuditLog.objects.exclude(action='LOGIN').order_by('-created_at')[:20]
        for l in logs:
            feed.append({
                'type': 'ACTION',
                'severity': 'LOW',
                'title': l.action,
                'pno': l.metadata.get('target_pno', 'N/A'),
                'name': l.officer.profile.name if l.officer else 'System',
                'timestamp': l.created_at.isoformat(),
                'details': l.metadata
            })
            
        # Sort combined feed
        feed.sort(key=lambda x: x['timestamp'], reverse=True)
        return Response({'status': 'success', 'data': feed[:40]})

class SystemAnalyticsView(APIView):
    """
    Returns time-series data for accuracy and anomaly spikes.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        from .models import PilotMetric
        metrics = PilotMetric.objects.all().order_by('-date')[:14] # last 14 days
        
        data = {
            'dates': [m.date.strftime('%d %b') for m in reversed(metrics)],
            'accuracy': [m.system_accuracy for m in reversed(metrics)],
            'anomalies': [m.alert_count for m in reversed(metrics)],
            'confidence': [m.avg_confidence for m in reversed(metrics)]
        }
        return Response({'status': 'success', 'data': data})

class RemoteConfigView(APIView):
    """
    Administrative control for system thresholds.
    """
    permission_classes = [IsAdminRole]

    def get(self, request):
        from .models import RemoteConfig
        configs = RemoteConfig.objects.all()
        return Response({'status': 'success', 'data': {c.key: c.value for c in configs}})

    def post(self, request):
        from .models import RemoteConfig, AuditLog
        key = request.data.get('key')
        value = request.data.get('value')
        
        old_val = "N/A"
        try:
            old_val = RemoteConfig.objects.get(key=key).value
        except RemoteConfig.DoesNotExist:
            pass

        config, _ = RemoteConfig.objects.get_or_create(key=key)
        config.value = value
        config.save()
        
        # Security: Audit Log for every config change
        AuditLog.objects.create(
            officer=request.user,
            actor=request.user,
            unit=request.user.unit,
            pno=request.user.pno,
            action='CONFIG_CHANGE',
            severity='SECURITY',
            metadata={
                'key': key,
                'old_value': old_val,
                'new_value': value,
                'ip': request.META.get('REMOTE_ADDR')
            }
        )
        
        return Response({'status': 'success', 'message': f'Config {key} updated'})
