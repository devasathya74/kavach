from django.utils import timezone
from ..models import ReadFenceSession

class SnapshotReadFence:
    """
    Consistency Fencing for Observers.
    Ensures that all reads during a snapshot observe the same revision.
    """
    
    @staticmethod
    def pin_revision(revision: int, reader_type: str) -> str:
        session = ReadFenceSession.objects.create(
            pinned_revision=revision,
            reader_type=reader_type
        )
        return str(session.id)

    @staticmethod
    def release_fence(session_id: str):
        ReadFenceSession.objects.filter(id=session_id).update(released_at=timezone.now())

class FenceEscalationPolicy:
    """
    Observer Performance Guard.
    Prevents long-running snapshots from paralyzing operational reads.
    """
    
    @staticmethod
    def evaluate_escalation(session_id: str):
        session = ReadFenceSession.objects.get(id=session_id)
        duration = (timezone.now() - session.started_at).total_seconds()
        
        if duration > 180: # 3 mins
            return "ABORT_SNAPSHOT"
        if duration > 60: # 1 min
            return "DEGRADE_NONCRITICAL_READS"
        if duration > 30: # 30s
            return "PARTIAL_STALE_WARNING"
            
        return "NORMAL"
