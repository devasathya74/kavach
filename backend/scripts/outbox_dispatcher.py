import os
import django
import time
from django.db import transaction, models
from django.utils import timezone

# Setup django environment
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import EventOutbox, DeadEvent, Officer
from apps.auth_app.orchestration.event_bus import EventBus

def run_outbox_dispatcher():
    """
    Transactional Outbox Poller.
    Ensures that events persisted in the outbox are dispatched with retries.
    """
    print("Starting Outbox Dispatcher...")
    while True:
        pending = EventOutbox.objects.filter(status='PENDING').order_by('created_at')[:50]
        
        for item in pending:
            try:
                # Dispatch via Bus (which handles WS/Push subscribers)
                actor = Officer.objects.get(id=item.actor_id) if item.actor_id else None
                unit = Officer.objects.filter(unit_id=item.unit_id).first().unit # Simple unit fetch
                
                EventBus.publish(
                    event_type=item.type,
                    actor=actor,
                    unit=unit,
                    payload=item.payload,
                    trace_id=item.trace_id
                )
                
                item.status = 'PROCESSED'
                item.save()
            except Exception as e:
                item.retry_count += 1
                if item.retry_count >= 5:
                    # Move to Dead Letter Queue
                    DeadEvent.objects.create(
                        original_id=item.event_id,
                        type=item.type,
                        payload=item.payload,
                        error_log=str(e)
                    )
                    item.status = 'FAILED'
                item.save()
                print(f"Outbox error: {str(e)}")
                
        time.sleep(2) # Polling interval

if __name__ == "__main__":
    run_outbox_dispatcher()
