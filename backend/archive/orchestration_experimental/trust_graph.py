from django.utils import timezone
from ..models import TrustDomain

class TemporalConfidenceDecay:
    """
    Freshness-Aware Reliability.
    Ensures that stale signals do not poison operational reasoning.
    """
    
    HALF_LIVES = {
        'NETWORK': 30,    # 30 seconds
        'DEVICE': 3600,   # 1 hour
        'REPLAY': 180,    # 3 minutes
        'OPERATOR': 86400 # 24 hours
    }

    @staticmethod
    def get_effective_score(domain: str) -> int:
        td = TrustDomain.objects.get(domain=domain)
        now = timezone.now()
        age = (now - td.updated_at).total_seconds()
        
        half_life = TemporalConfidenceDecay.HALF_LIVES.get(domain, 60)
        # Simple linear decay for demonstration
        decay = (age / half_life) * 10
        return max(0, int(td.trust_score - decay))

class TrustGraph:
    """
    Context-Aware Dependency Mapping.
    Links trust domains to reflect cascading reliability impacts.
    """
    
    @staticmethod
    def apply_cascading_impacts():
        # Example: Network instability reduces Replay confidence
        network_score = TemporalConfidenceDecay.get_effective_score('NETWORK')
        
        if network_score < 50:
            # Reduce REPLAY trust score proportionally
            pass
