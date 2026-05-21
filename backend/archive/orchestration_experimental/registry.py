from ..models import PartitionState, TrustDomain

class AutonomyConfidenceGate:
    """
    Safe Autonomous Governance.
    Dynamically reduces autonomous authority when environmental uncertainty is high.
    """
    
    @staticmethod
    def get_authorized_level() -> str:
        # 1. Check Global Trust
        avg_trust = sum([d.trust_score for d in TrustDomain.objects.all()]) / 5
        
        # 2. Check Partition State
        is_partitioned = PartitionState.objects.filter(current_state='PARTITIONED').exists()
        
        if is_partitioned or avg_trust < 40:
            return 'RESTRICTED' # Human-in-the-loop required for everything
            
        if avg_trust < 70:
            return 'BOUNDED' # Human approval required for P0/P1 actions
            
        return 'FULL' # Normal autonomous self-healing

class ArchitecturalIntentRegistry:
    """
    Long-Term Maintainability.
    Registers the "WHY" behind every critical subsystem and invariant.
    """
    
    REGISTRY = {
        'EventOutbox': {
            'intent': 'Ensures at-least-once delivery of operational events.',
            'invariant': 'Never commit business state without an outbox record.',
            'risk': 'Silent state divergence between nodes.'
        },
        'SnapshotReadFence': {
            'intent': 'Guarantees temporal read consistency during state capture.',
            'invariant': 'All readers must observe the same revision until capture completes.',
            'risk': 'Inconsistent forensic snapshots.'
        }
    }
