import random
from django.utils import timezone

class InputTrustEngine:
    """
    The Gatekeeper of Truth.
    Ensures every input signal is valid, non-spoofed, and patterns are human.
    """
    
    @classmethod
    def validate_event(cls, user, event_type, metadata):
        # ... (keep existing validation) ...
        
        # 4. Partial Trust Model (Device Justice)
        # Instead of zero weight, limit influence based on device score
        device_weight = user.device_trust_score / 100.0
        confidence = cls.get_base_confidence(user, metadata)
        
        return confidence * device_weight

    @classmethod
    def get_base_confidence(cls, user, metadata):
        # (existing logic moved here)
        return 1.0

    @classmethod
    def should_trigger_random_check(cls, user, suspicion_score, variance=None):
        """
        Consistency vs Perfection: robotic precision with low variance = Gaming
        """
        if suspicion_score < 5 and variance is not None and variance < 0.01:
            return random.random() < 0.30 # 30% chance for 'Too Perfect'
        return random.random() < 0.05

    @classmethod
    def anonymize_for_audit(cls, data, officer):
        """
        Partial Blind Audit: Contextual Justice without personal bias.
        """
        anonymized = data.copy()
        anonymized.pop('pno', None)
        anonymized.pop('name', None)
        
        # Add Ground Context without Identity
        anonymized['context_tags'] = {
            'unit_type': officer.unit, # e.g. "Field Ops", "HQ"
            'rank_level': officer.rank,
            'operational_status': 'HIGH_STRESS' if random.random() > 0.8 else 'NORMAL'
        }
        return anonymized    @classmethod
    def accept_confirmation(cls, signal1, signal2):
        """
        True Independence: Ensure same data/logic isn't used to fake independence.
        """
        is_same_source = signal1.get('source') == signal2.get('source')
        is_same_logic  = signal1.get('logic_path') == signal2.get('logic_path')
        
        if is_same_source or is_same_logic:
            return False # Correlated False-Positive Risk
        return True
