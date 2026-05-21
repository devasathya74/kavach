import logging
from django.db import transaction
from django.utils import timezone
from .models import Officer, OverrideRequest, OfficerActivity, OfficerProfile

logger = logging.getLogger('kavach.security')

def apply_rank_change(target_user: Officer, new_value: str) -> bool:
    try:
        # Validate that the new rank is actually valid
        valid_ranks = [choice[0] for choice in Officer.Rank.choices]
        if new_value not in valid_ranks:
            logger.error(f"Invalid rank override attempt: {new_value}")
            return False
            
        profile = target_user.profile
        profile.rank = new_value # Note: Expecting RankMaster instance or similar logic
        profile.save(update_fields=['rank', 'updated_at'])
        return True
    except Exception as e:
        logger.error(f"Rank change failed: {str(e)}")
        return False

def apply_status_change(target_user: Officer, new_value: str) -> bool:
    try:
        # Expected 'ACTIVE' or 'SUSPENDED' etc. mapped to is_active / is_blocked
        if new_value.upper() == 'ACTIVE':
            target_user.is_active = True
            target_user.is_blocked = False
        elif new_value.upper() == 'SUSPENDED':
            target_user.is_active = False
            target_user.is_blocked = True
        else:
            return False
            
        target_user.save(update_fields=['is_active', 'is_blocked', 'updated_at'])
        return True
    except Exception as e:
        logger.error(f"Status change failed: {str(e)}")
        return False

# Mapping of field identifiers to strict deterministic handlers
ALLOWED_OVERRIDE_FIELDS = {
    "rank": apply_rank_change,
    "status": apply_status_change,
}

class OverrideEngine:
    """
    Deterministic Override Apply Engine to prevent generic setattr() abuse.
    Includes Self-Target Protection to block rogue pilots.
    """
    
    @staticmethod
    def validate_request(pilot: Officer, target_user: Officer, field: str) -> tuple[bool, str]:
        # 1. Pilot Self-Target Protection
        if pilot.pno == target_user.pno:
            return False, "Pilot cannot modify their own record."
            
        # 2. Command Protection (Cannot modify CO or Superusers)
        if target_user.role in ['ADMIN', 'SUPERUSER']:
            return False, "Pilot cannot modify command-level officers."
            
        # 3. Prevent modifying own Commanding Officer explicitly
        if target_user == pilot.reporting_to:
            return False, "Pilot cannot modify their own Commanding Officer."
            
        # 4. Whitelist Field Verification
        if field not in ALLOWED_OVERRIDE_FIELDS:
            return False, f"Field '{field}' is not permitted for pilot override."
            
        return True, "Valid"

    @staticmethod
    def create_pending_request(pilot: Officer, target_user: Officer, field: str, new_value: str, reason: str, metadata: dict) -> PilotOverrideRequest:
        is_valid, err_msg = OverrideEngine.validate_request(pilot, target_user, field)
        if not is_valid:
            raise ValueError(f"Override validation failed: {err_msg}")
            
        # Extract previous value safely
        previous_value = str(getattr(target_user, field, "UNKNOWN"))
        if field == 'status':
            previous_value = "ACTIVE" if target_user.is_active and not target_user.is_blocked else "SUSPENDED"

        # Auto-timeout set to 7 days
        expires_at = timezone.now() + timezone.timedelta(days=7)

        req = OverrideRequest.objects.create(
            pilot=pilot,
            target_user=target_user,
            unit=pilot.unit,
            field=field,
            previous_value=previous_value,
            proposed_value=new_value,
            reason=reason,
            status='PENDING',
            device_fingerprint=metadata.get('device_id', ''),
            integrity_state=metadata.get('integrity', 'UNKNOWN'),
            expires_at=expires_at
        )
        
        # Audit Log for Request Creation
        OfficerActivity.objects.create(
            unit=pilot.unit,
            pno=pilot.pno,
            officer=pilot,
            action="OVERRIDE_REQUEST_CREATED",
            result="PENDING",
            severity="WARNING",
            metadata={"target_pno": target_user.pno, "field": field, "request_id": str(req.id)}
        )
        return req

    @staticmethod
    def ratify_request(request_id: str, co: Officer, action: str, metadata: dict) -> bool:
        """
        CO ratification (APPROVE or REJECT) with Dual Audit.
        Ensures atomicity and prevents concurrency via select_for_update().
        """
        with transaction.atomic():
            # Acquire database row lock
            request = OverrideRequest.objects.select_for_update().get(id=request_id)
            
            if request.status != 'PENDING':
                raise ValueError("Request is no longer pending or has already been processed.")
                
            # Self-Approval Protection
            if co.pno == request.pilot.pno:
                raise PermissionError("Pilot cannot approve their own request.")

            # Update Dual Audit Fields
            request.commanding_officer = co
            request.approved_by_ip = metadata.get('ip', '')
            request.approved_device_id = metadata.get('device_id', '')
            request.approved_integrity_level = metadata.get('integrity', 'UNKNOWN')
            request.approval_correlation_id = metadata.get('correlation_id', '')
            request.resolved_at = timezone.now()

            if action.upper() == 'APPROVE':
                handler = ALLOWED_OVERRIDE_FIELDS.get(request.field)
                if not handler:
                    request.status = 'REJECTED'
                    request.save()
                    raise ValueError("No handler available for field.")
                    
                success = handler(request.target_user, request.proposed_value)
                request.status = 'APPROVED' if success else 'REJECTED'
                request.save()
                
                # Dual Audit Log
                OfficerActivity.objects.create(
                    unit=co.unit,
                    pno=co.pno,
                    officer=co,
                    action="OVERRIDE_APPROVED" if success else "OVERRIDE_FAILED",
                    result="SUCCESS" if success else "FAILED",
                    severity="SECURITY",
                    metadata={"request_id": str(request.id), "target": request.target_user.pno}
                )
                return success
                
            elif action.upper() == 'REJECT':
                request.status = 'REJECTED'
                request.save()
                
                # Dual Audit Log
                OfficerActivity.objects.create(
                    unit=co.unit,
                    pno=co.pno,
                    officer=co,
                    action="OVERRIDE_REJECTED",
                    result="SUCCESS",
                    severity="SECURITY",
                    metadata={"request_id": str(request.id), "target": request.target_user.pno}
                )
                return True
                
            return False
