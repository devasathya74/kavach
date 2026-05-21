import logging
from .stability import EthicalBoundaries

logger = logging.getLogger(__name__)

class MissionPrimacyConstraint:
    """
    Civilizational Humility.
    Hardcodes the rule: Mission Success > System Continuity.
    """
    
    @staticmethod
    def evaluate_sacrifice(capability: str) -> bool:
        """
        Determines if a system capability (e.g. Telemetry, Replay)
        should be sacrificed to preserve human operational agency.
        """
        # Hard Rule: If human operational capability is at risk, drop telemetry
        SACRIFICEABLE = ['TELEMETRY_DETAIL', 'FORENSIC_GRANULARITY', 'REPLAY_DEPTH']
        if capability in SACRIFICEABLE:
            logger.info(f"MissionPrimacy: Sacrificing {capability} for mission continuity.")
            return True
        return False

class HumanFallbackProtocols:
    """
    Dependency Decoupling.
    Defines explicit manual workflows for platform failure scenarios.
    """
    
    @staticmethod
    def initiate_offline_quorum():
        # Step-by-step instructions for paper-based/verbal quorum
        return {
            'protocol': 'OFFLINE_VERBAL_RATIFICATION',
            'steps': [
                'Officer-1 generates manual ID',
                'Officer-2 signs verbal ack',
                'CO records intent in physical log'
            ]
        }
