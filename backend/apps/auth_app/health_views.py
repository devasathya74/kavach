from rest_framework import viewsets
from rest_framework.response import Response
from rest_framework.decorators import action
from ..models import EventOutbox, NotificationDelivery, Incident, RealtimeSession
from django.utils import timezone

class SystemHealthViewSet(viewsets.ViewSet):
    """
    Operational Health Cockpit.
    Provides actionable metrics for battalion oversight.
    """
    
    @action(detail=False, methods=['get'])
    def status(self, request):
        now = timezone.now()
        
        # 1. Outbox Backlog
        outbox_backlog = EventOutbox.objects.filter(status='PENDING').count()
        outbox_status = 'GREEN' if outbox_backlog < 100 else 'YELLOW' if outbox_backlog < 1000 else 'RED'
        
        # 2. WebSocket Presence
        active_sockets = RealtimeSession.objects.filter(last_heartbeat__gt=now - timezone.timedelta(seconds=90)).count()
        
        # 3. Delivery Failure Rate (Last 1hr)
        failed_deliveries = NotificationDelivery.objects.filter(
            status='FAILED', 
            created_at__gt=now - timezone.timedelta(hours=1)
        ).count()
        
        return Response({
            'timestamp': now,
            'panels': {
                'outbox': {
                    'count': outbox_backlog,
                    'status': outbox_status,
                    'remediation': 'Restart OutboxDispatcher if RED'
                },
                'realtime': {
                    'active_connections': active_sockets,
                    'status': 'GREEN' if active_sockets > 0 else 'BLACK',
                    'remediation': 'Verify WebSocket Gateway health'
                },
                'delivery_failures': {
                    'count': failed_deliveries,
                    'status': 'GREEN' if failed_deliveries < 10 else 'RED',
                    'remediation': 'Check FCM credentials or network route'
                }
            }
        })
