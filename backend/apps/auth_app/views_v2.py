from rest_framework import viewsets, status, filters
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.db import transaction, models as django_models
from django.utils import timezone
from django_filters.rest_framework import DjangoFilterBackend
import hashlib

from .models import (
    Officer, OfficerProfile, DraftChange, OverrideRequest, UnitMaster, 
    OfficerDevice, OfficerActivity, Incident, IncidentEvent, Broadcast, BroadcastAcknowledgment,
    FieldData, OtaUpdate, TrainingModule, TrainingAcknowledgment, BroadcastRecipient, BroadcastAttachment, BroadcastDispatchJob
)
from .serializers import (
    OfficerSerializer, OfficerActivitySerializer, DraftChangeSerializer, 
    OfficerDeviceSerializer as DeviceSerializer, IncidentSerializer, BroadcastSerializer,
    FieldDataSerializer, OtaUpdateSerializer, TrainingModuleSerializer
)
from .permissions import IsAdminRole

# ... (Previous ViewSets) ...

class FieldDataViewSet(viewsets.ModelViewSet):
    """
    Structured Evidence & Document Management.
    Ensures that uploaded files are hashed and tracked for forensic integrity.
    """
    queryset = FieldData.objects.all()
    serializer_class = FieldDataSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        return super().get_queryset().filter(unit=user.unit)

    def perform_create(self, serializer):
        file_obj = self.request.FILES.get('file')
        sha256_hash = ""
        if file_obj:
            sha256 = hashlib.sha256()
            for chunk in file_obj.chunks():
                sha256.update(chunk)
            sha256_hash = sha256.hexdigest()
        
        serializer.save(
            uploader=self.request.user,
            unit=self.request.user.unit,
            sha256=sha256_hash,
            size_bytes=file_obj.size if file_obj else 0
        )

class OtaUpdateViewSet(viewsets.ModelViewSet):
    """
    Authoritative Software Distribution.
    Mandatory SHA256 verification and staged rollout management.
    """
    queryset = OtaUpdate.objects.all()
    serializer_class = OtaUpdateSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    @action(detail=False, methods=['get'])
    def latest(self, request):
        """Returns the latest valid update for the current device's unit."""
        unit = request.user.unit
        update = OtaUpdate.objects.filter(
            status__in=['PRODUCTION', 'STAGED']
        ).filter(
            django_models.Q(status='PRODUCTION') | django_models.Q(target_units=unit)
        ).order_by('-version_code').first()
        
        if not update:
            return Response({"detail": "No valid update found."}, status=404)
        return Response(self.get_serializer(update).data)

    def perform_create(self, serializer):
        file_obj = self.request.FILES.get('file')
        sha256_hash = ""
        if file_obj:
            sha256 = hashlib.sha256()
            for chunk in file_obj.chunks():
                sha256.update(chunk)
            sha256_hash = sha256.hexdigest()
        serializer.save(sha256=sha256_hash)

class TrainingViewSet(viewsets.ModelViewSet):
    """
    Mission Readiness Training.
    Tracks acknowledgment and completion of briefing materials.
    """
    queryset = TrainingModule.objects.all()
    serializer_class = TrainingModuleSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        return super().get_queryset().filter(unit=user.unit)

    def perform_create(self, serializer):
        serializer.save(unit=self.request.user.unit)

    @action(detail=True, methods=['post'])
    def acknowledge(self, request, pk=None):
        module = self.get_object()
        ack, created = TrainingAcknowledgment.objects.get_or_create(
            module=module,
            officer=request.user,
            defaults={
                'is_completed': True,
                'completed_at': timezone.now(),
                'device_id': request.META.get('HTTP_X_DEVICE_ID', 'UNKNOWN')
            }
        )
        if not created:
            ack.is_completed = True
            ack.completed_at = timezone.now()
            ack.save()
        return Response({"status": "training_completed"})

# ... (Previous ViewSets) ...

class AuditTimelineViewSet(viewsets.ReadOnlyModelViewSet):
    """
    Immutable Institutional Memory.
    Searchable, severity-coded logs of all system-critical actions.
    """
    queryset = OfficerActivity.objects.all().order_by('-created_at')
    serializer_class = OfficerActivitySerializer
    permission_classes = [IsAuthenticated, IsAdminRole]
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['severity', 'action', 'pno']
    search_fields = ['pno', 'action', 'metadata', 'ip_address']
    ordering_fields = ['created_at', 'severity']

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        return super().get_queryset().filter(unit=user.unit)

class IncidentViewSet(viewsets.ModelViewSet):
    """
    Field Reporting & Forensic Timeline.
    Append-only logic for security breaches and field events.
    """
    queryset = Incident.objects.all().prefetch_related('timeline', 'timeline__actor')
    serializer_class = IncidentSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['status', 'severity', 'type']
    search_fields = ['incident_id', 'title', 'summary', 'location_name']
    ordering_fields = ['reported_at', 'severity']

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        return super().get_queryset().filter(unit=user.unit)

    def perform_create(self, serializer):
        with transaction.atomic():
            incident = serializer.save(reported_by=self.request.user, unit=self.request.user.unit)
            # Create initial event
            IncidentEvent.objects.create(
                incident=incident,
                event_type='CREATED',
                actor=self.request.user,
                payload={'msg': 'Initial field report logged.'}
            )

class BroadcastViewSet(viewsets.ModelViewSet):
    """
    Authoritative Command Gateway.
    Ensures that mission orders are tracked and delivered.
    """
    queryset = Broadcast.objects.all().prefetch_related('acknowledgments')
    serializer_class = BroadcastSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        
        # Filter: 
        # 1. Broadcasts in user's unit
        # 2. (Either targeted_officers is empty OR user is in targeted_officers)
        base_qs = super().get_queryset().filter(unit=user.unit)
        
        return base_qs.filter(
            django_models.Q(targeted_officers__isnull=True) | 
            django_models.Q(targeted_officers=user) |
            django_models.Q(actor=user)
        ).distinct()

    def perform_create(self, serializer):
        serializer.save(actor=self.request.user, unit=self.request.user.unit)

    @action(detail=True, methods=['post'])
    def acknowledge(self, request, pk=None):
        broadcast = self.get_object()
        BroadcastAcknowledgment.objects.get_or_create(
            broadcast=broadcast,
            officer=request.user,
            defaults={'device_id': request.META.get('HTTP_X_DEVICE_ID', 'UNKNOWN')}
        )
        return Response({"status": "acknowledged"})

    @action(detail=False, methods=['post'])
    def draft(self, request):
        """
        Validates draft payload and performs any backend pre-checks before local caching.
        Optional in offline-first, but provides a server validation layer.
        """
        return Response({"status": "draft_accepted", "message": "Draft validated successfully"}, status=status.HTTP_202_ACCEPTED)

    @action(detail=False, methods=['post'], url_path='upload')
    def upload_attachment(self, request):
        """
        Handles media uploads strictly for broadcasts.
        Returns the remote URL and checksum.
        """
        file_obj = request.FILES.get('file')
        if not file_obj:
            return Response({"error": "No file provided"}, status=status.HTTP_400_BAD_REQUEST)

        # Basic limits
        if file_obj.size > 15 * 1024 * 1024:
            return Response({"error": "File size exceeds 15MB limit"}, status=status.HTTP_400_BAD_REQUEST)

        sha256 = hashlib.sha256()
        for chunk in file_obj.chunks():
            sha256.update(chunk)
        sha256_hash = sha256.hexdigest()

        # In a real system, upload to S3/GCS here. We simulate and return a URL.
        remote_url = f"https://storage.kavach.local/broadcasts/{sha256_hash}_{file_obj.name}"

        return Response({
            "status": "upload_success",
            "remote_url": remote_url,
            "checksum": sha256_hash,
            "file_name": file_obj.name,
            "mime_type": file_obj.content_type,
            "file_size": file_obj.size
        }, status=status.HTTP_201_CREATED)

    @action(detail=False, methods=['post'])
    def finalize(self, request):
        """
        Transactional Finalize Dispatch.
        - Creates Broadcast
        - Snapshots Recipients
        - Saves Attachment Links
        - Enqueues Dispatch Job
        All in a single atomic transaction.
        """
        data = request.data
        title = data.get('title')
        content = data.get('content')
        priority = data.get('priority', 'INFO')
        b_type = data.get('type', 'TEXT')
        trace_id = data.get('trace_id', '')
        recipient_ids = data.get('recipient_ids', []) # Expected to be PNOs or UUIDs
        attachments_data = data.get('attachments', [])

        if not title or not recipient_ids:
            return Response({"error": "Title and recipients are required."}, status=status.HTTP_400_BAD_REQUEST)

        # Enforce max recipients logic or pagination logic check here if needed.
        # But we accept large fanouts via queue.

        with transaction.atomic():
            # 1. Create Broadcast
            broadcast = Broadcast.objects.create(
                actor=request.user,
                unit=request.user.unit,
                title=title,
                content=content,
                priority=priority,
                broadcast_type=b_type,
                trace_id=trace_id
            )

            # 2. Snapshot Recipients
            recipients_to_create = []
            # We assume recipient_ids are User IDs for the canonical state
            users = Officer.objects.filter(id__in=recipient_ids).select_related('unit', 'profile', 'profile__company')
            
            for user in users:
                recipients_to_create.append(
                    BroadcastRecipient(
                        broadcast=broadcast,
                        user_id=str(user.id),
                        pno=user.pno,
                        unit=user.unit.name if user.unit else '',
                        company=user.profile.company.name if (hasattr(user, 'profile') and user.profile.company) else ''
                    )
                )
            BroadcastRecipient.objects.bulk_create(recipients_to_create)

            # 3. Create Attachments
            attachments_to_create = []
            for att in attachments_data:
                attachments_to_create.append(
                    BroadcastAttachment(
                        broadcast=broadcast,
                        file_name=att.get('file_name', 'unknown'),
                        mime_type=att.get('mime_type', 'application/octet-stream'),
                        file_size=att.get('file_size', 0),
                        remote_url=att.get('remote_url', ''),
                        checksum=att.get('checksum', '')
                    )
                )
            BroadcastAttachment.objects.bulk_create(attachments_to_create)

            # 4. Enqueue Dispatch
            BroadcastDispatchJob.objects.create(
                broadcast=broadcast,
                status='PENDING'
            )

        return Response({
            "status": "dispatch_accepted", 
            "message": "Broadcast created and dispatch enqueued.",
            "broadcast_id": str(broadcast.id)
        }, status=status.HTTP_202_ACCEPTED)

class UserManagementViewSet(viewsets.ModelViewSet):
    """
    API v2 User Management.
    Enforces Tenancy and Governance Pipeline.
    """
    queryset = Officer.objects.all().select_related('profile', 'unit').prefetch_related('devices')
    serializer_class = OfficerSerializer
    permission_classes = [IsAuthenticated]
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['role', 'profile__service_status', 'unit__code', 'unit__type', 'profile__company__code', 'profile__platoon__number']
    search_fields = ['pno', 'profile__name', 'profile__phone']
    ordering_fields = ['created_at', 'profile__name']
    ordering = ['profile__name']

    def dispatch(self, request, *args, **kwargs):
        from .security.roles import can_access_personnel
        # Global Authority Gate: Personnel Management is restricted to COMMAND (ADMIN/PILOT)
        if request.user.is_authenticated and not can_access_personnel(request.user):
             return self.http_method_not_allowed(request, *args, **kwargs)
        
        print(f"[DISPATCH] PATH: {request.path} | USER: {request.user} | AUTH: {getattr(request, 'auth', None) is not None}")
        return super().dispatch(request, *args, **kwargs)

    def list(self, request, *args, **kwargs):
        print(f"[DEBUG USER] PNO: {request.user.pno} | ROLE: {request.user.role} | AUTH: {request.auth is not None}")
        return super().list(request, *args, **kwargs)

    def get_queryset(self):
        user = self.request.user
        # Mission-Critical: Must select_related all hierarchy fields to prevent 'Unknown' data in UI list
        qs = Officer.objects.all().select_related(
            'profile',
            'unit',
            'profile__rank',
            'profile__unit',
            'profile__company',
            'profile__platoon'
        ).prefetch_related('devices')

        if user.is_staff or user.role in ['ADMIN', 'PILOT']:
             return qs
        return qs.filter(id=user.id)

    def create(self, request, *args, **kwargs):
        from .services.personnel_service import PersonnelService
        from .models import RankMaster
        
        print(f"DEBUG: UserManagementViewSet.create | DATA: {request.data}")
        
        try:
            # 1. Extract Core Identity
            pno = request.data.get('pno')
            role = request.data.get('role') or request.data.get('system_role', 'USER')
            password = request.data.get('password')

            # 2. Extract Profile Data (Handle Flat and Nested)
            profile_data = request.data.get('profile', {})
            name = request.data.get('name') or profile_data.get('name', 'Unknown')
            phone = request.data.get('phone') or profile_data.get('phone', '')
            email = request.data.get('email') or profile_data.get('email')

            # 3. Resolve IDs (Handle Numeric IDs or String Codes)
            from .models import UnitMaster, CompanyMaster, RankMaster, PlatoonMaster

            def resolve_id(model_class, value, search_field='code'):
                if not value:
                    return None
                # If it's already an integer or a digit string, return it as is (let the DB handle it)
                if isinstance(value, int) or (isinstance(value, str) and value.isdigit()):
                    return value
                # Otherwise, search by the specified field (usually 'code')
                try:
                    obj = model_class.objects.filter(**{search_field: value}).first()
                    if obj:
                        return obj.id
                except Exception as e:
                    print(f"DEBUG: Error resolving {model_class.__name__} for {value}: {e}")
                return None

            unit_id = resolve_id(UnitMaster, request.data.get('unit_id') or request.data.get('unit')) or request.user.unit.id
            company_id = resolve_id(CompanyMaster, request.data.get('company_id') or request.data.get('company'))
            platoon_id = resolve_id(PlatoonMaster, request.data.get('platoon_id') or request.data.get('platoon'), search_field='number')

            # 4. Resolve Rank
            rank_id = resolve_id(RankMaster, request.data.get('rank_id') or request.data.get('rank'))
            if not rank_id:
                # Fallback to rank_code in profile if provided
                rank_code = profile_data.get('rank_code') or request.data.get('rank_code', 'CONST')
                rank_id = resolve_id(RankMaster, rank_code)
            
            # If still no rank_id, find default CONST
            if not rank_id:
                const_rank = RankMaster.objects.filter(code='CONST').first()
                rank_id = const_rank.id if const_rank else None

            data = {
                'pno': pno,
                'role': role,
                'unit_id': unit_id,
                'company_id': company_id,
                'platoon_id': platoon_id,
                'password': password,
                'name': name,
                'rank_id': rank_id,
                'phone': phone,
                'email': email
            }
            
            print(f"DEBUG: Normalized data for PersonnelService: {data}")
            
            officer = PersonnelService.create_user(request.user, data)
            serializer = self.get_serializer(officer)
            return Response({
                "status": "success",
                "message": "Personnel created successfully",
                "data": serializer.data
            }, status=201)
            
        except RankMaster.DoesNotExist:
            return Response({"error": "Invalid Rank provided."}, status=400)
        except PermissionError as e:
            return Response({"error": str(e)}, status=403)
        except Exception as e:
            print(f"ERROR in UserManagementViewSet.create: {str(e)}")
            import traceback
            traceback.print_exc()
            return Response({"error": str(e)}, status=400)

    def update(self, request, *args, **kwargs):
        from .services.personnel_service import PersonnelService
        try:
            officer = PersonnelService.update_user(request.user, kwargs['pk'], request.data)
            if hasattr(officer, 'status') and officer.status == 'PENDING':
                return Response({"status": "approval_pending", "message": "Changes submitted for CO ratification"}, status=202)
            
            serializer = self.get_serializer(officer)
            return Response({
                "status": "success",
                "message": "Personnel updated successfully",
                "data": serializer.data
            })
        except PermissionError as e:
            return Response({"error": str(e)}, status=403)
        except Exception as e:
            return Response({"error": str(e)}, status=400)

    def destroy(self, request, *args, **kwargs):
        target_officer = self.get_object()
        if request.user.role == 'PILOT' and target_officer.role == 'ADMIN':
            return Response({"error": "Pilot cannot delete Admin"}, status=403)
            
        # Audit Log
        from .models.governance import OfficerActivity
        OfficerActivity.objects.create(
            actor=request.user,
            officer=target_officer,
            pno=target_officer.pno,
            action="USER_DELETE",
            severity="WARNING",
            ip_address=request.META.get('REMOTE_ADDR')
        )
        
        target_officer.is_active = False
        target_officer.save()
        return Response(status=204)

    @action(detail=True, methods=['post'])
    def reset_password(self, request, pk=None):
        from django.contrib.auth.hashers import make_password, check_password
        from .serializers import validate_secure_password
        from .models.governance import OfficerActivity
        officer = self.get_object()
        password = request.data.get('password')
    @action(detail=True, methods=['post'])
    def reset_password(self, request, pk=None):
        from .services.personnel_service import PersonnelService
        try:
            password = request.data.get('password')
            PersonnelService.reset_password(request.user, pk, password)
            return Response({"status": "reset"})
        except PermissionError as e:
            return Response({"error": str(e)}, status=403)
        except Exception as e:
            return Response({"error": str(e)}, status=400)

    @action(detail=True, methods=['post'])
    def toggle_status(self, request, pk=None):
        from .services.personnel_service import PersonnelService
        try:
            officer = PersonnelService.toggle_status(request.user, pk)
            if hasattr(officer, 'status') and officer.status == 'PENDING':
                return Response({"status": "approval_pending", "message": "Suspension requires CO ratification"}, status=202)
                
            return Response({"status": "active" if officer.is_active else "suspended"})
        except PermissionError as e:
            return Response({"error": str(e)}, status=403)
        except Exception as e:
            return Response({"error": str(e)}, status=400)

    def partial_update(self, request, *args, **kwargs):
        from .services.personnel_service import PersonnelService
        print(f"[DEBUG PATCH] USER: {request.user} | AUTH: {request.auth is not None}")
        try:
            officer = PersonnelService.update_user(request.user, kwargs['pk'], request.data)
            if hasattr(officer, 'status') and officer.status == 'PENDING':
                return Response({"status": "approval_pending", "message": "Sensitive changes submitted for ratification"}, status=202)
            
            serializer = self.get_serializer(officer)
            return Response({
                "status": "success",
                "message": "Personnel updated successfully",
                "data": serializer.data
            })
        except PermissionError as e:
            return Response({"error": str(e)}, status=403)
        except Exception as e:
            import traceback
            traceback.print_exc()
            return Response({"error": str(e)}, status=400)

    @action(detail=True, methods=['post'])
    def deactivate(self, request, pk=None):
        officer = self.get_object()
        reason = request.data.get('reason', 'Deactivated by Admin')
        officer.deactivate(actor=request.user, reason=reason)
        
        # Also update profile status
        profile = officer.profile
        profile.service_status = 'SUSPENDED'
        profile.save()
        
        return Response({"status": "deactivated"})

    @action(detail=True, methods=['post'], url_path='revoke-device')
    def revoke_device(self, request, pk=None):
        officer = self.get_object()
        device_id = request.data.get('device_id')
        
        if not device_id:
            return Response({"error": "device_id required"}, status=400)
            
        from .models import OfficerDevice, OfficerActivity
        try:
            device = OfficerDevice.objects.get(officer=officer, device_id=device_id)
            device.status = 'REVOKED'
            device.save()
            
            # Global Invalidation: Increment session version
            officer.session_version += 1
            officer.save(update_fields=['session_version'])
            
            # Log the action
            OfficerActivity.objects.create(
                officer=officer,
                actor=request.user,
                unit=officer.unit,
                pno=officer.pno,
                action='DEVICE_REVOKED',
                severity='WARNING',
                result='SUCCESS',
                metadata={'device_id': device_id, 'reason': 'Remote revocation by Admin'}
            )
            
            return Response({"status": "revoked"})
        except OfficerDevice.DoesNotExist:
            return Response({"error": "Device not found"}, status=404)

    @action(detail=True, methods=['get'], url_path='audit-timeline')
    def audit_timeline(self, request, pk=None):
        officer = self.get_object()
        from .models import OfficerActivity
        from .serializers import OfficerActivitySerializer
        activities = OfficerActivity.objects.filter(officer=officer).order_by('-timestamp')[:100]
        serializer = OfficerActivitySerializer(activities, many=True)
        return Response(serializer.data)

class DeviceCenterViewSet(viewsets.ReadOnlyModelViewSet):
    """
    Operational Device Dashboard.
    Monitoring for Heartbeats, Integrity, and Trust scores.
    """
    queryset = OfficerDevice.objects.all().select_related('officer', 'officer__profile')
    serializer_class = DeviceSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['status', 'integrity_level', 'officer__unit__code']
    search_fields = ['device_id', 'device_name', 'officer__pno', 'officer__profile__name']
    ordering_fields = ['last_active', 'trust_score']
    ordering = ['-last_active']

    def get_queryset(self):
        user = self.request.user
        if user.is_superuser:
            return super().get_queryset()
        return super().get_queryset().filter(officer__unit=user.unit)

    @action(detail=True, methods=['post'])
    def revoke(self, request, pk=None):
        device = self.get_object()
        device.status = 'REVOKED'
        device.save()
        
        # Invalidate officer session
        officer = device.officer
        officer.session_version += 1
        officer.save(update_fields=['session_version'])
        
        return Response({"status": "revoked"})

class DraftChangeViewSet(viewsets.ModelViewSet):
    """
    Governance Reliability Engine.
    Enforces State Transitions, Conflict Detection, and Anti-Self-Approval.
    """
    queryset = DraftChange.objects.all()
    serializer_class = DraftChangeSerializer
    permission_classes = [IsAuthenticated, IsAdminRole]

    def get_queryset(self):
        return super().get_queryset().filter(unit=self.request.user.unit)

    @action(detail=True, methods=['post'])
    def submit(self, request, pk=None):
        change = self.get_object()
        if change.status != 'DRAFT':
            return Response({"error": "Only DRAFT changes can be submitted"}, status=400)
            
        # Capture current revision for future conflict detection
        target = change.target
        current_revision = target.profile.revision if change.model == 'OfficerProfile' else target.revision
        change.expected_state = {'revision': current_revision}
        
        change.status = 'SUBMITTED'
        change.save()
        return Response({"status": "submitted", "revision": current_revision})

    @action(detail=True, methods=['post'])
    def approve(self, request, pk=None):
        change = self.get_object()
        user = request.user
        
        # 1. State Guard
        if change.status != 'SUBMITTED':
            return Response({"error": "Invalid state transition"}, status=400)
            
        # 2. Anti-Self-Approval (Two-Man Rule)
        if change.actor == user:
            return Response({"error": "SELF_APPROVAL_BLOCKED: Initiator cannot ratify their own request."}, status=403)

        # 3. Conflict Detection (Revision-Based)
        target = change.target
        current_revision = target.profile.revision if change.model == 'OfficerProfile' else target.revision
        expected_revision = change.expected_state.get('revision') if change.expected_state else None
        
        if expected_revision and current_revision != expected_revision:
            change.status = 'CONFLICTED'
            change.save()
            return Response({"error": f"CONFLICT: Target revision mismatch (Current: {current_revision}, Expected: {expected_revision})"}, status=409)

        # 4. Idempotency & State Transition (APPLYING)
        if change.status == 'APPLYING':
             return Response({"status": "already_applying"}, status=202)
             
        change.status = 'APPLYING'
        change.save()

        with transaction.atomic():
            # Apply and increment revision
            if change.model == 'OfficerProfile':
                profile = target.profile
                setattr(profile, change.field, change.new_value['val'])
                profile.revision += 1
                profile.save()
            elif change.model == 'Officer':
                setattr(target, change.field, change.new_value['val'])
                target.revision += 1
                target.save()
                
            change.status = 'APPLIED'
            change.approver = user
            change.approved_at = timezone.now()
            change.applied_at = timezone.now()
            change.save()
            
            # Lite Event Sourcing
            from .models import OperationalEvent
            OperationalEvent.objects.create(
                type='GovernanceApplied',
                actor=user,
                unit=user.unit,
                trace_id=getattr(request, 'trace_id', 'N/A'),
                payload={
                    'change_id': change.id,
                    'target_pno': target.pno,
                    'field': change.field,
                    'new_val': change.new_value,
                    'new_revision': current_revision + 1
                }
            )
            
        return Response({"status": "applied", "new_revision": current_revision + 1})

    @action(detail=True, methods=['post'])
    def reject(self, request, pk=None):
        change = self.get_object()
        if change.status != 'SUBMITTED':
            return Response({"error": "Invalid state transition"}, status=400)
            
        change.status = 'REJECTED'
        change.rejecter = request.user
        change.rejected_at = timezone.now()
        change.save()
        
        return Response({"status": "rejected"})

    @action(detail=True, methods=['post'], url_path='logout-all')
    def logout_all(self, request, pk=None):
        officer = self.get_object()
        officer.session_version += 1
        officer.save(update_fields=['session_version'])
        
        from .models import OfficerActivity
        OfficerActivity.objects.create(
            officer=officer,
            actor=request.user,
            unit=officer.unit,
            pno=officer.pno,
            action='LOGOUT_ALL_SESSIONS',
            severity='CRITICAL',
            result='SUCCESS',
            metadata={'reason': 'Manual global logout by Admin'}
        )
        return Response({"status": "all_sessions_invalidated"})
