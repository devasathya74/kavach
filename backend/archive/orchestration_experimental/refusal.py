import logging

logger = logging.getLogger(__name__)

class GovernanceRefusalEngine:
    """
    Architectural Maturity Shield.
    Enforces the "permission to stop expanding" by refusing unnecessary growth.
    """
    
    @staticmethod
    def evaluate_proposal(justification: dict) -> bool:
        """
        Rejects proposals that don't significantly:
        1. Reduce complexity
        2. Improve field survivability
        3. Strengthen human agency
        """
        # Forbidden: more intelligence, recursive cognition, symbolic sophistication
        RED_FLAGS = ['RECURSIVE_COGNITION', 'SYMBOLIC_LAYERING', 'AI_EXPANSION']
        
        for flag in RED_FLAGS:
            if flag in justification.get('tags', []):
                logger.error(f"GovernanceRefusal: Proposal {justification.get('key')} rejected as symbolic inflation.")
                return False
                
        # Required: proven complexity delta (must be negative)
        if justification.get('complexity_delta', 0) >= 0:
            logger.warning(f"GovernanceRefusal: Proposal {justification.get('key')} adds net complexity. Rejection mandated by Stewardship Rule.")
            return False
            
        return True
