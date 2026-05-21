from datetime import timedelta
from django.utils import timezone
from ..models import Incident, IncidentDraft

class DraftConflictResolver:
    """
    Operational State Reconciliation.
    Ensures that offline drafts are merged safely into the server state.
    """
    
    @staticmethod
    def resolve_incident_draft(draft_data: dict, server_incident: Incident = None):
        """
        Merge Strategy:
        - If no server incident: Safe to create.
        - If server incident exists (duplicate sync): Compare revision/hashes.
        """
        if not server_incident:
            return "CREATE", draft_data

        # 1. Duplicate Detection
        if str(server_incident.id) == draft_data.get('server_id'):
            return "ALREADY_SYNCED", None

        # 2. State Divergence Check
        if server_incident.status == 'ARCHIVED':
            return "REJECT_ARCHIVED", None
            
        # 3. Revision check would go here if incidents were mutable
        return "SAFE_SYNC", draft_data

class IdempotencyEngine:
    @staticmethod
    def is_processed(key: str) -> bool:
        from ..models import IdempotencyRecord
        return IdempotencyRecord.objects.filter(key=key, expires_at__gt=timezone.now()).exists()

    @staticmethod
    def record(key: str, response: dict, ttl_hours: int = 24):
        from ..models import IdempotencyRecord
        IdempotencyRecord.objects.update_or_create(
            key=key,
            defaults={
                'response': response,
                'expires_at': timezone.now() + timedelta(hours=ttl_hours)
            }
        )
