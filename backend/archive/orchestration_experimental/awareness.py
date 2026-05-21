from django.utils import timezone
from ..models import RealtimeSession, NotificationDelivery, EventOutbox

class CorrelatedIncidentDetector:
    """
    Operational Inference Engine.
    Detects regional outages or system-wide issues by correlating low-level signals.
    """
    
    @staticmethod
    def detect_system_anomalies():
        now = timezone.now()
        one_hour_ago = now - timezone.timedelta(hours=1)
        
        # Signal 1: WebSocket Session Churn
        stale_sessions = RealtimeSession.objects.filter(last_heartbeat__lt=now - timezone.timedelta(seconds=180)).count()
        
        # Signal 2: Delivery Failure Rate
        delivery_failures = NotificationDelivery.objects.filter(
            status='FAILED',
            created_at__gt=one_hour_ago
        ).count()
        
        # Signal 3: Outbox Backlog Depth
        outbox_backlog = EventOutbox.objects.filter(status='PENDING').count()

        # Inference Logic (Synthetic Operational Incident)
        if stale_sessions > 50 and delivery_failures > 20:
            return {
                'type': 'REGIONAL_CONNECTIVITY_OUTAGE',
                'confidence': 0.85,
                'root_cause': 'WebSocket + Delivery Cluster Collapse',
                'severity': 'HIGH'
            }
            
        if outbox_backlog > 5000:
            return {
                'type': 'DISPATCHER_SATURATION',
                'confidence': 0.90,
                'root_cause': 'Outbox backlog exceeding throughput',
                'severity': 'MEDIUM'
            }
            
        return None
