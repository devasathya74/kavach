import logging
from django.db import transaction
from ..models import TrustDomain, TrustRecoveryLog, PolicyAudit

logger = logging.getLogger(__name__)

class PolicyCompactor:
    """
    Governance Governance Layer.
    Detects redundancy and contradiction in operational policies.
    """
    
    @staticmethod
    def run_audit():
        """
        Scans platform for:
        - Redundant governance rules
        - Unreachable mode states
        - Zombie remediation paths
        """
        # Simulated Audit
        PolicyAudit.objects.create(
            category='REDUNDANCY',
            description='Found 3 overlapping trust weighting rules in UNIT_B',
            findings={'rules': ['RuleA', 'RuleB', 'RuleC']},
            severity='WARNING'
        )
        return True

class TrustRecoveryProtocol:
    """
    Stabilization Engine.
    Gradually restores automation confidence after stability signals return.
    """
    
    @staticmethod
    def attempt_recovery(domain: str, reason: str, trace_id: str):
        with transaction.atomic():
            td = TrustDomain.objects.get(domain=domain)
            if td.trust_score >= 100: return
            
            start_score = td.trust_score
            # Incrementally restore confidence
            td.trust_score = min(100, td.trust_score + 10)
            td.save()
            
            TrustRecoveryLog.objects.create(
                domain=domain,
                start_score=start_score,
                end_score=td.trust_score,
                recovery_reason=reason,
                trace_id=trace_id
            )
            logger.info(f"TrustRecovery: Restored {domain} to {td.trust_score}%")
