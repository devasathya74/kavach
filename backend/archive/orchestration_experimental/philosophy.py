import logging
from ..models import ExplainabilityLevel, SemanticDrift

logger = logging.getLogger(__name__)

class HierarchicalExplainability:
    """
    Cognitive Friction Management.
    Filters operational truth to prevent situational overload across command tiers.
    """
    
    @staticmethod
    def get_tailored_explanation(level: str, trace_data: dict) -> dict:
        mask = ExplainabilityLevel.objects.get(level_key=level).visibility_mask
        # Logic to filter technical trace_data based on the audience's visibility mask
        return {k: v for k, v in trace_data.items() if k in mask}

class SemanticGovernanceEngine:
    """
    Era-Scale Intent Guard.
    Detects when the meaning of critical mission terms begins to drift.
    """
    
    @staticmethod
    def validate_term_consistency(term: str, current_usage: str):
        record = SemanticDrift.objects.get(term=term)
        # Simplified drift check
        if len(current_usage) != len(record.original_meaning): # placeholder logic
            record.conflict_detected = True
            record.current_reinterpretation = current_usage
            record.save()
            logger.warning(f"SemanticDrift: Detected potential meaning drift for term: {term}")
        return not record.conflict_detected
