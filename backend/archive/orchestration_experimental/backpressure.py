import time
from django.utils import timezone
from ..models import EventOutbox, RealtimeSession

class SignalConfidenceGraph:
    """
    Temporal Anomaly Weighting.
    Prevents false incidents by decaying the importance of old monitoring signals.
    """
    
    @staticmethod
    def calculate_signal_weight(timestamp) -> float:
        now = timezone.now()
        age_seconds = (now - timestamp).total_seconds()
        
        # Exponential Decay
        if age_seconds < 30: return 1.0 # 100% confidence
        if age_seconds < 300: return 0.5 # 50% confidence (5 mins)
        return 0.1 # 10% confidence

class BackpressureCoordinator:
    """
    Global Operational Throttling.
    Propagates pressure signals from Backend -> WebSocket -> Android.
    """
    
    @staticmethod
    def get_system_pressure() -> str:
        # 1. Check Outbox Backlog
        backlog = EventOutbox.objects.filter(status='PENDING').count()
        
        if backlog > 10000: return 'CRITICAL'
        if backlog > 2000: return 'HIGH'
        if backlog > 500: return 'MODERATE'
        return 'STABLE'

    @staticmethod
    def get_throttle_policy() -> dict:
        pressure = BackpressureCoordinator.get_system_pressure()
        
        policies = {
            'STABLE': {'throttle_bulk': False, 'coalesce_telemetry': False, 'reject_silent': False},
            'MODERATE': {'throttle_bulk': True, 'coalesce_telemetry': False, 'reject_silent': False},
            'HIGH': {'throttle_bulk': True, 'coalesce_telemetry': True, 'reject_silent': False},
            'CRITICAL': {'throttle_bulk': True, 'coalesce_telemetry': True, 'reject_silent': True}
        }
        return policies.get(pressure, policies['STABLE'])
