from django.db import transaction
from django.utils import timezone
from .event_bus import EventBus
from .incident_policy import IncidentPolicy
from ..models import DraftChange, OfficerActivity, Incident, IncidentEvent

class GovernanceCommand:
    @staticmethod
    def approve_change(change_id: int, approver, trace_id: str):
        """
        Transactional Orchestration of a Governance Approval.
        NOT a simple database save.
        """
        with transaction.atomic():
            change = DraftChange.objects.select_for_update().get(id=change_id)
            
            # 1. State Machine Guard
            if change.status != 'SUBMITTED':
                raise ValueError("Invalid state for approval")
                
            # 2. Anti-Self-Approval
            if change.actor == approver:
                raise PermissionError("Self-approval blocked")

            # 3. Conflict Check (Revision Based)
            target = change.target
            current_revision = target.profile.revision if change.model == 'OfficerProfile' else target.revision
            expected_revision = change.expected_state.get('revision')
            
            if expected_revision and current_revision != expected_revision:
                change.status = 'CONFLICTED'
                change.save()
                raise ValueError("State conflict detected")

            # 4. Apply Change
            change.status = 'APPLYING'
            change.save()
            
            if change.model == 'OfficerProfile':
                profile = target.profile
                setattr(profile, change.field, change.new_value['val'])
                profile.revision += 1
                profile.save()
            else:
                setattr(target, change.field, change.new_value['val'])
                target.revision += 1
                target.save()
                
            change.status = 'APPLIED'
            change.approver = approver
            change.approved_at = timezone.now()
            change.applied_at = timezone.now()
            change.save()
            
            # 5. Publish Domain Event
            EventBus.publish(
                event_type='GovernanceApplied',
                actor=approver,
                unit=approver.unit,
                trace_id=trace_id,
                payload={
                    'change_id': change.id,
                    'target_pno': target.pno,
                    'field': change.field,
                    'new_revision': current_revision + 1
                }
            )
            
            return change

class IncidentCommand:
    @staticmethod
    def create_incident(actor, data: dict, trace_id: str):
        """
        Append-Only Incident Creation.
        Orchestrates Persistence -> Timeline -> Event Bus.
        """
        with transaction.atomic():
            severity = data.get('severity', 'MEDIUM')
            incident_id = IncidentPolicy.generate_incident_id(actor.unit.code)
            
            # 1. Create Core Record
            incident = Incident.objects.create(
                incident_id=incident_id,
                type=data.get('type'),
                title=data.get('title'),
                summary=data.get('summary'),
                severity=severity,
                reported_by=actor,
                unit=actor.unit,
                latitude=data.get('latitude'),
                longitude=data.get('longitude'),
                occurred_at=data.get('occurred_at', timezone.now()),
                metadata=data.get('metadata', {})
            )
            
            # 2. Append Forensic Timeline
            IncidentEvent.objects.create(
                incident=incident,
                event_type='CREATED',
                actor=actor,
                trace_id=trace_id,
                payload={'initial_state': data}
            )
            
            # 3. Publish Domain Event
            EventBus.publish(
                event_type='IncidentCreated',
                actor=actor,
                unit=actor.unit,
                trace_id=trace_id,
                payload={
                    'incident_id': incident_id,
                    'type': incident.type,
                    'severity': severity,
                    'title': incident.title
                }
            )
            
            return incident
