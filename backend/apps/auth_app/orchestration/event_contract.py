import uuid
from datetime import datetime
from typing import Any, Dict

class StandardEvent:
    """
    Formal Event Contract for Kavach Realtime System.
    Ensures consistency, deduplication, and ordering.
    """
    @staticmethod
    def wrap(event_type: str, payload: Dict[str, Any], entity_id: str = None, version: int = 1) -> Dict[str, Any]:
        return {
            "event_id": str(uuid.uuid4()),
            "type": event_type,
            "entity_id": entity_id,
            "version": version,
            "timestamp": datetime.now().isoformat(),
            "payload": payload
        }
