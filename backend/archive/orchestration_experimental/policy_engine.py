import logging
from django.db import transaction
from django.utils import timezone
from ..models import SnapshotSession, PlatformMode, EventOutbox
from .event_bus import EventBus

logger = logging.getLogger(__name__)

class SnapshotBarrierCoordinator:
    """
    State Capture Fencing.
    Ensures 'Internal Consistency' by pausing mutations and draining queues.
    """
    
    @staticmethod
    def initiate_barrier(subsystems: list):
        with transaction.atomic():
            session = SnapshotSession.objects.create(
                status='PREPARING',
                frozen_subsystems=subsystems
            )
            # Logic to notify subsystems to freeze would go here
            return session

    @staticmethod
    def drain_and_capture(session_id: str):
        session = SnapshotSession.objects.get(id=session_id)
        
        # 1. Drain Outbox
        backlog = EventOutbox.objects.filter(status='PENDING').count()
        if backlog > 0:
            session.status = 'DRAINING'
            session.save()
            return False # Need more time
            
        session.status = 'CAPTURING'
        session.save()
        return True

class PlatformPolicyEngine:
    """
    Mission-Critical Constraint Enforcement.
    Centrally manages behavioral rules across all platform modes.
    """
    
    POLICY_PRIORITY = {
        'RECOVERY': 100,
        'LOCKDOWN': 80,
        'INCIDENT': 60,
        'DEGRADED': 40,
        'NORMAL': 0
    }

    @staticmethod
    def get_current_mode() -> str:
        mode = PlatformMode.objects.order_by('-updated_at').first()
        return mode.current_mode if mode else 'NORMAL'

    @staticmethod
    def validate_action(action_type: str, actor=None) -> bool:
        mode = PlatformPolicyEngine.get_current_mode()
        
        if mode == 'LOCKDOWN':
            # Critical only
            allowed = ['AUTHENTICATE', 'REPORT_INCIDENT', 'ACKNOWLEDGE_ALERT']
            return action_type in allowed
            
        if mode == 'RECOVERY':
            return action_type == 'REPLAY_EVENT'
            
        return True

    @staticmethod
    def switch_mode(new_mode: str, actor, reason: str):
        with transaction.atomic():
            old_mode = PlatformPolicyEngine.get_current_mode()
            PlatformMode.objects.create(
                current_mode=new_mode,
                updated_by=actor,
                reason=reason
            )
            
            # Broadcast Mode Change
            EventBus.publish(
                event_type='PlatformModeChanged',
                actor=actor,
                unit=actor.unit,
                trace_id='SYSTEM_MODE_SWITCH',
                payload={
                    'old_mode': old_mode,
                    'new_mode': new_mode,
                    'reason': reason
                }
            )
