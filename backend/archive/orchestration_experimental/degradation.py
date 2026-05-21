class QuorumPolicy:
    """
    Multi-Authority Enforcement.
    Ensures that critical state transitions require verified consensus.
    """
    
    REQUIREMENTS = {
        'LOCKDOWN_EXIT': 2,
        'RECOVERY_EXIT': 2,
        'GLOBAL_REVOKE': 2,
        'GOVERNANCE_BYPASS': 3
    }

    @staticmethod
    def is_quorum_met(action: str, approved_count: int) -> bool:
        required = QuorumPolicy.REQUIREMENTS.get(action, 1)
        return approved_count >= required

class DegradationMatrix:
    """
    Subsystem-Aware Operational Throttling.
    Defines exactly which capabilities are preserved in constrained modes.
    """
    
    @staticmethod
    def get_capabilities(mode: str) -> dict:
        matrix = {
            'NORMAL': {'realtime': True, 'media': True, 'governance': 'OPEN'},
            'DEGRADED': {'realtime': False, 'media': False, 'governance': 'RESTRICTED'},
            'LOCKDOWN': {'realtime': True, 'media': False, 'governance': 'FROZEN'},
            'PARTITIONED': {'realtime': False, 'media': False, 'governance': 'FROZEN'}
        }
        return matrix.get(mode, matrix['NORMAL'])
