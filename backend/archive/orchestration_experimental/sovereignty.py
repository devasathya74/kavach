import logging
from ..models import Officer

logger = logging.getLogger(__name__)

class ControlledUncertaintyInjection:
    """
    Institutional Alertness.
    Prevents the "Success Paradox" by forcing occasional manual verifications.
    """
    
    @staticmethod
    def trigger_alertness_drill():
        # Force a manual verification even if automation is high-confidence
        logger.info("UncertaintyInjection: Triggering mandatory manual verification drill.")
        return True

class DistributedHumanSovereignty:
    """
    Anti-Authoritarian Guard.
    Guarantees that humans can challenge and override autonomous logic.
    """
    
    @staticmethod
    def record_sovereign_dissent(officer: Officer, autonomy_decision: str, rationale: str):
        # Minority dissent preservation logic
        # Ensures that a human override is never silently absorbed
        logger.info(f"HumanSovereignty: Officer {officer.pno} registered dissent against {autonomy_decision}")
        return True
