import logging
from django.db.models import Sum
from ..models import ExceptionDebt, GovernanceConstraint

logger = logging.getLogger(__name__)

class EntropyCeilingGuard:
    """
    Stabilization Force.
    Enforces the Exception Entropy Ceiling to prevent institutional decay.
    """
    
    @staticmethod
    def verify_governance_stability():
        # Check cumulative unresolved exception impact
        total_debt = ExceptionDebt.objects.filter(is_reconciled=False).aggregate(Sum('impact_score'))['impact_score__sum'] or 0
        
        ceiling = GovernanceConstraint.objects.get(constraint_key='EXCEPTION_DEBT_CEILING')
        ceiling.current_value = total_debt
        
        if total_debt > ceiling.ceiling_threshold:
            ceiling.is_violated = True
            logger.error("EntropyCeiling: EXCEPTION DEBT CRITICAL. Triggering mandatory governance slowdown.")
            # Logic to restrict autonomous authority until debt is reconciled
        else:
            ceiling.is_violated = False
            
        ceiling.save()
        return not ceiling.is_violated

class HumilityProtocol:
    """
    Institutional Realism.
    Reminds the platform and its operators that failure remains possible.
    """
    
    @staticmethod
    def get_realism_warning() -> str:
        return (
            "KAVACH remains a humble tool under human command. "
            "Technical sophistication does not grant immunity from "
            "institutional error, corruption, or mission failure."
        )
