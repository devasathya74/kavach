from .models import SystemState
from .utils import get_avg_delivery_delay
from apps.behavior.models import Violation
from django.utils import timezone
from datetime import timedelta
try:
    import psutil
except ImportError:
    psutil = None

class AdaptiveEngine:
    BASE_ACK_LIMIT = 300 # seconds
    MIN_LIMIT = 300
    MAX_LIMIT = 900

    def __init__(self):
        self.state, _ = SystemState.objects.get_or_create(id=1)

    def calculate_current_factor(self):
        # 1. System Load (CPU)
        cpu = psutil.cpu_percent() if psutil else 10.0
        cpu_signal = min(1.0, cpu / 90.0) # Scale 0-90 to 0-1
        
        # 2. Network Delay (Delivery)
        delay = get_avg_delivery_delay()
        delay_signal = min(1.0, delay / 120.0) # Scale 0-120s to 0-1
        
        # 3. Mass Failure Rate
        # Ratio of violations in last hour vs total users (dummy denominator 100 for now)
        v_count = Violation.objects.filter(created_at__gt=timezone.now() - timedelta(hours=1)).count()
        failure_signal = min(1.0, v_count / 20.0) # Scale 0-20 to 0-1

        # Final Weighted Factor
        factor = (cpu_signal * 0.3) + (delay_signal * 0.4) + (failure_signal * 0.3)
        
        # Update State
        self.state.current_adaptive_factor = factor
        self.state.cpu_load = cpu
        self.state.network_delay_sec = delay
        self.state.failure_rate = v_count
        self.state.save()
        
        return factor

    def get_dynamic_ack_threshold(self):
        if not self.state.adaptive_mode:
            return self.BASE_ACK_LIMIT
            
        factor = self.calculate_current_factor()
        threshold = self.BASE_ACK_LIMIT * (1 + factor)
        
        # Clamp within hard boundaries
        return max(self.MIN_LIMIT, min(self.MAX_LIMIT, threshold))
