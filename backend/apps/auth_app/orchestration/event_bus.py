import logging
from typing import Callable, Dict, List, Any
from django.db import transaction
from ..models import OperationalEvent, EventOutbox
import uuid

logger = logging.getLogger(__name__)

class EventBus:
    """
    Central Nervous System for Operational Coordination.
    Decouples business actions from side-effects (Audit, Notifications, Analytics).
    """
    _handlers: Dict[str, List[Callable]] = {}

    @classmethod
    def subscribe(cls, event_type: str, handler: Callable):
        if event_type not in cls._handlers:
            cls._handlers[event_type] = []
        cls._handlers[event_type].append(handler)

    @classmethod
    def publish(cls, event_type: str, actor, unit, payload: Dict[Any, Any], trace_id: str):
        """
        Transactional Publish.
        Persistence (Outbox) -> Dispatcher (Realtime).
        """
        event_id = uuid.uuid4()
        
        # 1. Transactional Outbox Persistence
        # In a real system, this would be wrapped in a transaction from the caller
        OperationalEvent.objects.create(
            id=event_id,
            type=event_type,
            actor=actor,
            unit=unit,
            payload=payload,
            trace_id=trace_id
        )
        
        EventOutbox.objects.create(
            event_id=event_id,
            type=event_type,
            actor_id=actor.id if actor else None,
            unit_id=unit.id,
            payload=payload,
            trace_id=trace_id,
            status='PENDING'
        )
        
        # 2. Immediate Dispatch (Best effort)
        handlers = cls._handlers.get(event_type, [])
        for handler in handlers:
            try:
                handler(event_type, actor, unit, payload, trace_id)
            except Exception as e:
                logger.error(f"Event handler failed: {str(e)}")

# --- Core Handlers (Side Effects) ---

def notify_incident_escalation(event_type, actor, unit, payload, trace_id):
    from .realtime_gateway import NotificationDispatcher
    NotificationDispatcher.handle_event(event_type, actor, unit, payload, trace_id)

# Register default handlers
EventBus.subscribe('IncidentCreated', notify_incident_escalation)
EventBus.subscribe('GovernanceApplied', notify_incident_escalation)
EventBus.subscribe('DeviceRevoked', notify_incident_escalation)
