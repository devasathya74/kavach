import time
import logging
from django.db import transaction
from ..models import EventOutbox, NotificationDelivery

logger = logging.getLogger(__name__)

class WeightedOutboxScheduler:
    """
    Multi-Queue Operational Dispatcher.
    Prevents Priority Starvation and handles Burst Protection.
    """
    
    # Weight = Number of items to process in one cycle
    WEIGHTS = {
        'CRITICAL': 50,
        'URGENT': 25,
        'IMPORTANT': 15,
        'INFO': 7,
        'SILENT': 3
    }

    @staticmethod
    def run_cycle():
        """
        Processes a weighted burst across all priority queues.
        Ensures CRITICAL events always have reserved execution slots.
        """
        for priority, weight in WeightedOutboxScheduler.WEIGHTS.items():
            pending = EventOutbox.objects.filter(
                status='PENDING',
                payload__priority=priority # Assuming priority is injected in payload
            ).order_by('created_at')[:weight]
            
            for item in pending:
                WeightedOutboxScheduler._dispatch(item)

    @staticmethod
    def _dispatch(item):
        from .event_bus import EventBus
        from .realtime_gateway import NotificationDispatcher
        
        try:
            with transaction.atomic():
                # 1. State Guard & Dispatch
                # In a real system, this would trigger WS/FCM handlers
                NotificationDispatcher.handle_event(
                    item.type, 
                    actor=None, # Re-hydration needed 
                    unit=None, 
                    payload=item.payload, 
                    trace_id=item.trace_id
                )
                
                item.status = 'PROCESSED'
                item.save()
        except Exception as e:
            item.retry_count += 1
            if item.retry_count >= 5:
                item.status = 'FAILED'
            item.save()
            logger.error(f"Outbox Dispatch Failure: {str(e)}")
