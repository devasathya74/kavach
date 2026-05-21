from django.db import models
import uuid

class OperationalEvent(models.Model):
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    type    = models.CharField(max_length=50)
    actor   = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, related_name='events_triggered')
    unit    = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    payload = models.JSONField()
    sequence       = models.BigIntegerField(default=0, db_index=True)
    schema_version = models.IntegerField(default=1)
    causation_id   = models.UUIDField(null=True, blank=True)
    correlation_id = models.UUIDField(null=True, blank=True)
    trace_id    = models.CharField(max_length=100, db_index=True)
    created_at  = models.DateTimeField(auto_now_add=True, db_index=True)
    class Meta:
        db_table = 'operational_events'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['created_at']),
            models.Index(fields=['correlation_id']),
            models.Index(fields=['sequence']),
        ]

class EventOutbox(models.Model):
    event_id   = models.UUIDField(unique=True)
    type       = models.CharField(max_length=50)
    actor_id   = models.IntegerField(null=True)
    unit_id    = models.IntegerField()
    payload    = models.JSONField()
    trace_id   = models.CharField(max_length=100)
    status     = models.CharField(max_length=20, default='PENDING', db_index=True)
    retry_count = models.IntegerField(default=0)
    sequence    = models.BigIntegerField(default=0, db_index=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    class Meta:
        db_table = 'event_outbox'
        ordering = ['created_at']
        indexes = [
            models.Index(fields=['status', 'created_at']),
            models.Index(fields=['sequence']),
        ]

class DeadEvent(models.Model):
    original_id = models.UUIDField(unique=True)
    type        = models.CharField(max_length=50)
    payload     = models.JSONField()
    error_log   = models.TextField()
    failed_at   = models.DateTimeField(auto_now_add=True)
    class Meta:
        db_table = 'dead_events'

class IdempotencyRecord(models.Model):
    key      = models.CharField(max_length=128, unique=True)
    response = models.JSONField()
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)
    class Meta:
        db_table = 'idempotency_records'

class DistributedLock(models.Model):
    key      = models.CharField(max_length=128, unique=True)
    owner    = models.CharField(max_length=100)
    expires_at = models.DateTimeField(db_index=True)
    class Meta:
        db_table = 'distributed_locks'

class PlatformMode(models.Model):
    MODES = [
        ('NORMAL', 'Standard Operation'),
        ('INCIDENT', 'Elevated Awareness'),
        ('DEGRADED', 'Low Bandwidth/Capacity'),
        ('LOCKDOWN', 'Restricted Mutation'),
        ('RECOVERY', 'Event Replay Only'),
    ]
    current_mode = models.CharField(max_length=20, choices=MODES, default='NORMAL')
    updated_at   = models.DateTimeField(auto_now=True)
    updated_by   = models.ForeignKey('Officer', on_delete=models.PROTECT)
    reason       = models.TextField()
    metadata     = models.JSONField(default=dict, blank=True)
    class Meta:
        db_table = 'platform_modes'

class RecoveryAction(models.Model):
    id          = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    trigger     = models.CharField(max_length=50)
    action      = models.CharField(max_length=100)
    subsystem   = models.CharField(max_length=50)
    confidence  = models.IntegerField()
    status      = models.CharField(max_length=20, default='STARTED', db_index=True)
    outcome     = models.TextField(blank=True)
    started_at  = models.DateTimeField(auto_now_add=True, db_index=True)
    reverted_at = models.DateTimeField(null=True, blank=True)
    class Meta:
        db_table = 'recovery_actions'
        indexes = [
            models.Index(fields=['status', 'started_at']),
        ]

class OperationalFSM(models.Model):
    STATES = [
        ('HEALTHY', 'Stable Operation'),
        ('DEGRADED', 'Performance Pressure'),
        ('PARTITIONED', 'Network Fragmentation'),
        ('HEALING', 'Reconciliation Phase'),
        ('RECOVERY', 'Disaster Restoration'),
        ('LOCKDOWN', 'Security Restriction'),
        ('FORENSIC', 'Audit Replay Mode'),
    ]
    current_state = models.CharField(max_length=20, choices=STATES, default='HEALTHY')
    previous_state = models.CharField(max_length=20, choices=STATES, null=True)
    transition_reason = models.TextField()
    trace_id      = models.CharField(max_length=100)
    actor         = models.ForeignKey('Officer', on_delete=models.PROTECT, null=True)
    created_at    = models.DateTimeField(auto_now_add=True, db_index=True)
    class Meta:
        db_table = 'operational_fsm'
        ordering = ['-created_at']

class OtaUpdate(models.Model):
    VERSION_STATUS = [
        ('STAGED', 'Staged Rollout'),
        ('PRODUCTION', 'Full Production'),
        ('DEPRECATED', 'Deprecated'),
        ('REVOKED', 'Revoked/Emergency Rollback'),
    ]
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    version_code = models.IntegerField(unique=True)
    version_name = models.CharField(max_length=20)
    file    = models.FileField(upload_to='ota_updates/')
    sha256  = models.CharField(max_length=64)
    changelog = models.TextField()
    is_mandatory = models.BooleanField(default=False)
    status  = models.CharField(max_length=20, choices=VERSION_STATUS, default='STAGED', db_index=True)
    target_units = models.ManyToManyField('UnitMaster', blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    released_at = models.DateTimeField(null=True, blank=True)
    class Meta:
        db_table = 'ota_updates'
        ordering = ['-version_code']
