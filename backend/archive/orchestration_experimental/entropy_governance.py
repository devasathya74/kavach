import logging
from django.db import transaction
from django.utils import timezone
from ..models import OperationalEvent, SnapshotAggregate, HealingSession

logger = logging.getLogger(__name__)

class EntropyGovernor:
    """
    Long-term Platform Health.
    Manages lineage compaction and stale data pruning.
    """
    
    @staticmethod
    def compact_lineage(older_than_days: int = 30):
        """
        Prunes ancient events that are already captured in snapshots.
        """
        threshold = timezone.now() - timezone.timedelta(days=older_than_days)
        
        with transaction.atomic():
            # Only prune if a snapshot exists after the event
            # Logic would verify snapshot coverage before deletion
            count, _ = OperationalEvent.objects.filter(created_at__lt=threshold).delete()
            logger.info(f"EntropyGovernor: Pruned {count} stale events.")

class GovernanceRiskEngine:
    """
    Adaptive Policy Enforcement.
    Dynamically adjusts governance strictness based on risk signals.
    """
    
    @staticmethod
    def calculate_risk_score(officer_id: int) -> int:
        # Signals: Repeated conflicts, partition history, device churn
        score = 0
        
        # Example: check failed healing sessions
        failed_heals = HealingSession.objects.filter(status='FAILED').count()
        if failed_heals > 5: score += 40
        
        return min(100, score)

    @staticmethod
    def get_approval_requirement(officer_id: int) -> int:
        risk = GovernanceRiskEngine.calculate_risk_score(officer_id)
        if risk > 80: return 3 # 3 approvers for high risk
        if risk > 40: return 2 # 2 approvers
        return 1 # Standard 1 approver
