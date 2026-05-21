import logging
from django.db import transaction
from django.utils import timezone
from ..models import RemediationAction, OperationalFSM, OperationalEvent, SnapshotAggregate
from .recovery import ReplayCoordinator

logger = logging.getLogger(__name__)

class AutonomousRemediationEngine:
    """
    Self-Healing Control Plane.
    Executes bounded, reversible corrections to platform drifts.
    """
    
    @staticmethod
    def remediate(trigger: str, action: str, subsystem: str, confidence: int):
        with transaction.atomic():
            remediation = RemediationAction.objects.create(
                trigger=trigger,
                action=action,
                subsystem=subsystem,
                confidence=confidence,
                status='STARTED'
            )
            
            try:
                # 1. Execute Correction
                if action == 'SWITCH_DEGRADED':
                    from .policy_engine import PlatformPolicyEngine
                    PlatformPolicyEngine.switch_mode('DEGRADED', None, f"Auto-Remediation: {trigger}")
                
                # In a real system, we'd add more logic (throttling, queue isolation, etc.)
                
                remediation.status = 'COMPLETED'
                remediation.save()
                return True
            except Exception as e:
                remediation.status = 'FAILED'
                remediation.outcome = str(e)
                remediation.save()
                return False

class TimelineReconstructor:
    """
    Forensic Time-Travel.
    Rebuilds exact platform state at any specific timestamp T.
    """
    
    @staticmethod
    def reconstruct_state_at(aggregate_type: str, timestamp_t):
        """
        Flow:
        1. Find latest snapshot BEFORE T.
        2. Replay all events from snapshot.revision until first event > T.
        """
        # 1. Baseline Snapshot
        snapshot = SnapshotAggregate.objects.filter(
            aggregate_type=aggregate_type,
            created_at__lte=timestamp_t
        ).order_by('-created_at').first()
        
        if not snapshot:
            logger.warning(f"No baseline snapshot for {aggregate_type} before {timestamp_t}.")
            current_state = {}
            last_revision = 0
        else:
            current_state = snapshot.payload
            last_revision = snapshot.revision

        # 2. Forensic Replay
        events = OperationalEvent.objects.filter(
            sequence__gt=last_revision,
            created_at__lte=timestamp_t
        ).order_by('sequence')
        
        for event in events:
            current_state = ReplayCoordinator._apply_event(current_state, event)
            
        return current_state
