import hashlib
import json
import logging
from django.db import transaction
from ..models import SnapshotAggregate, OperationalEvent

logger = logging.getLogger(__name__)

class SnapshotService:
    @staticmethod
    def create_snapshot(aggregate_type: str, state_data: dict, revision: int, actor=None):
        """Creates an immutable recovery point."""
        payload_str = json.dumps(state_data)
        checksum = hashlib.sha256(payload_str.encode()).hexdigest()
        
        with transaction.atomic():
            return SnapshotAggregate.objects.create(
                aggregate_type=aggregate_type,
                revision=revision,
                payload=state_data,
                sha256=checksum,
                created_by=actor
            )

class ReplayCoordinator:
    """
    Forensic State Reconstruction.
    Replays events from a specific baseline to rebuild the current state.
    """
    
    @staticmethod
    def rebuild_state(aggregate_type: str, target_revision: int = None):
        # 1. Fetch latest snapshot
        snapshot = SnapshotAggregate.objects.filter(aggregate_type=aggregate_type).order_by('-revision').first()
        if not snapshot:
            logger.warning(f"No snapshot found for {aggregate_type}. Replaying from Genesis.")
            current_state = {}
            last_revision = 0
        else:
            current_state = snapshot.payload
            last_revision = snapshot.revision

        # 2. Replay events after snapshot revision
        events = OperationalEvent.objects.filter(
            sequence__gt=last_revision
        ).order_by('sequence')
        
        if target_revision:
            events = events.filter(sequence__lte=target_revision)

        for event in events:
            # 3. Gap Detection
            if event.sequence != last_revision + 1:
                logger.error(f"GAP_DETECTED: Expected {last_revision + 1}, Found {event.sequence}")
                # In a real system, trigger a ReplayRequest for the missing range
                if not ReplayCoordinator._recover_gap(last_revision + 1, event.sequence - 1):
                    raise Exception("NON_RECOVERABLE_GAP: Aborting replay to prevent corruption.")

            current_state = ReplayCoordinator._apply_event(current_state, event)
            last_revision = event.sequence
            
        return current_state, last_revision

    @staticmethod
    def _recover_gap(start_seq, end_seq):
        """Attempts to fetch missing events from the archival store or peer nodes."""
        # Verification logic
        missing_count = EventOutbox.objects.filter(sequence__gte=start_seq, sequence__lte=end_seq).count()
        return missing_count == (end_seq - start_seq + 1)

    @staticmethod
    def _apply_event(state: dict, event: OperationalEvent):
        """
        Idempotent Event Application.
        (Implementation details depend on the specific aggregate)
        """
        # Placeholder for domain-specific replay logic
        return state
