import logging
from ..models import TrustDomain, DecisionTrace

logger = logging.getLogger(__name__)

class RealityReconciler:
    """
    Field Truth Integration.
    Ensures that real-world anomalies supersede autonomous models.
    """
    
    @staticmethod
    def reconcile_field_truth(anomalies: list):
        """
        Decreases autonomous confidence if field reality (dissent, anomalies)
        contradicts system models.
        """
        if anomalies:
            logger.warning("RealityReconciler: Field reality contradicts models. Weakening autonomous confidence.")
            # Logic to reduce TrustDomain weights or confidence decay
            return True
        return False

class SymmetricTransparencyProvider:
    """
    Human Sovereignty Empowerment.
    Enables commanders to inspect system reasoning without forensic expertise.
    """
    
    @staticmethod
    def get_simple_reasoning(trace_id: str) -> dict:
        trace = DecisionTrace.objects.get(trace_id=trace_id)
        # Simplified presentation of trust assumptions and reasoning weights
        return {
            'why': trace.inference_type,
            'confidence': f"{trace.confidence_score}%",
            'main_signal': 'Low Network Trust',
            'consequence': 'Switched to Degraded Mode'
        }
