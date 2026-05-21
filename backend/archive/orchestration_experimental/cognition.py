import logging
from django.utils import timezone
from ..models import PlatformMode, TrustDomain, OperationalEvent

logger = logging.getLogger(__name__)

class GovernanceHysteresis:
    """
    Policy Stability Engine.
    Prevents 'Mode Flapping' by requiring persistence windows for transitions.
    """
    
    @staticmethod
    def can_transition(from_mode: str, to_mode: str) -> bool:
        last_change = PlatformMode.objects.order_by('-updated_at').first()
        if not last_change: return True
        
        duration = (timezone.now() - last_change.updated_at).total_seconds()
        
        # Stability Rules
        if from_mode == 'LOCKDOWN' and to_mode == 'NORMAL':
            return duration > 300 # Must stay in Lockdown for 5m before exit
            
        if from_mode == 'NORMAL' and to_mode == 'LOCKDOWN':
            return True # Emergency entry is always allowed
            
        return duration > 30 # Default 30s hysteresis

class OperationalReasoner:
    """
    Cognitive Operational Analysis.
    Infers probable causes from correlated distributed patterns.
    """
    
    @staticmethod
    def infer_operational_status(trace_id: str = 'INTERNAL') -> dict:
        from ..models import DecisionTrace
        # 1. Gather signals from TrustDomains
        network_trust = TrustDomain.objects.get(domain='NETWORK').trust_score
        device_trust = TrustDomain.objects.get(domain='DEVICE').trust_score
        
        triggering_signals = {'network': network_trust, 'device': device_trust}
        
        # 2. Pattern Analysis
        inference = 'STABLE'
        action = 'NONE'
        confidence = 1.0
        
        if network_trust < 40:
            inference = 'PROBABLE_REGIONAL_OUTAGE'
            confidence = 0.85
            action = 'SWITCH_DEGRADED'
            
        elif device_trust < 50:
            inference = 'DEVICE_INTEGRITY_COMPROMISE'
            confidence = 0.90
            action = 'RAISE_QUORUM_REQUIREMENT'
            
        # 3. Record Trace
        DecisionTrace.objects.create(
            inference_type=inference,
            triggering_signals=triggering_signals,
            signal_weights={'network': 0.6, 'device': 0.4},
            remediation_chosen=action,
            confidence_score=int(confidence * 100),
            trace_id=trace_id
        )
            
        return {'inference': inference, 'confidence': confidence, 'action': action}
