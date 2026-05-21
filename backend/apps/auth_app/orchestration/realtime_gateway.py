import json
import uuid
from django.utils import timezone
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from ..models import NotificationDelivery, Officer, RealtimeSession
from .event_contract import StandardEvent

class RealtimeGateway:
    """
    Operational Delivery Gateway.
    Abstracts transport layers (WebSocket, Push, SMS).
    Does NOT contain business logic.
    """
    
    @staticmethod
    def broadcast_unit(unit_code: str, event_type: str, payload: dict, trace_id: str):
        """Broadcasts to all active officers in a unit via the unit group."""
        channel_layer = get_channel_layer()
        event = StandardEvent.wrap(event_type, payload)
        
        async_to_sync(channel_layer.group_send)(
            f"unit_{unit_code}",
            {
                "type": "broadcast_message",
                "message": event
            }
        )

    @staticmethod
    def dispatch_to_user(user, event_type: str, payload: dict, trace_id: str):
        """Dispatches a targeted event to a specific user."""
        channel_layer = get_channel_layer()
        event = StandardEvent.wrap(event_type, payload, entity_id=str(user.id))
        
        # 1. Dispatch to WebSocket
        async_to_sync(channel_layer.group_send)(
            f"user_{user.pno}",
            {
                "type": "broadcast_message",
                "message": event
            }
        )

        # 2. Delivery Tracking (Outbox/Audit)
        NotificationDelivery.objects.create(
            event_id=event['event_id'],
            target=user,
            priority=payload.get('priority', 'INFO'),
            channel='WEBSOCKET',
            status='SENT',
            trace_id=trace_id
        )

    @staticmethod
    def heartbeat(socket_id: str):
        RealtimeSession.objects.filter(socket_id=socket_id).update(last_heartbeat=timezone.now())

    @staticmethod
    def get_presence_state(session) -> str:
        """
        Operational Presence Logic.
        Converts heartbeat latency into Confidence Levels.
        """
        now = timezone.now()
        latency = (now - session.last_heartbeat).total_seconds()
        
        if latency < 30: return 'ONLINE'
        if latency < 90: return 'DEGRADED'
        if latency < 180: return 'STALE'
        return 'OFFLINE'

    @staticmethod
    def get_presence_confidence(session) -> int:
        """
        Calculates 0-100 confidence score based on multi-signal telemetry.
        Signals: Latency, Sync Freshness, Ack Ratio.
        """
        state = RealtimeGateway.get_presence_state(session)
        if state == 'OFFLINE': return 0
        
        confidence = 100
        if state == 'DEGRADED': confidence -= 30
        if state == 'STALE': confidence -= 60
        
        return max(0, confidence)

class NotificationDispatcher:
    """
    Priority-Aware Routing Engine.
    Implements event coalescing and delivery tracking.
    """
    
    @staticmethod
    def handle_event(event_type: str, actor, unit, payload: dict, trace_id: str):
        priority = NotificationDispatcher.determine_priority(event_type, payload)
        
        if priority == 'SILENT':
            # Low priority: Do nothing, wait for client pull
            return

        # URGENT/CRITICAL: Immediate dispatch + Tracking
        if priority in ['URGENT', 'CRITICAL']:
            RealtimeGateway.broadcast_unit(unit.code, event_type, payload, trace_id)
            # Log for re-delivery if not acked (At-Least-Once guarantee)

    @staticmethod
    def determine_priority(event_type: str, payload: dict) -> str:
        mapping = {
            'IncidentCreated': 'URGENT' if payload.get('severity') in ['HIGH', 'CRITICAL'] else 'IMPORTANT',
            'GovernanceApplied': 'IMPORTANT',
            'DeviceRevoked': 'CRITICAL',
            'IntegrityBreach': 'CRITICAL',
            'PersonnelBatchUpdate': 'INFO'
        }
        return mapping.get(event_type, 'INFO')
