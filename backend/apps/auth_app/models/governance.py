from django.db import models
import uuid

class OfficerActivity(models.Model):
    SEVERITY_CHOICES = [
        ('INFO', 'Information'),
        ('WARNING', 'Warning'),
        ('CRITICAL', 'Critical'),
        ('SECURITY', 'Security Alert'),
    ]
    officer    = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, related_name='activities')
    actor      = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, related_name='actions_performed')
    unit       = models.ForeignKey('UnitMaster', on_delete=models.PROTECT, null=True, blank=True)
    pno        = models.CharField(max_length=9)
    action              = models.CharField(max_length=100)
    severity            = models.CharField(max_length=10, choices=SEVERITY_CHOICES, default='INFO', db_index=True)
    related_incident_id = models.UUIDField(null=True, blank=True)
    route          = models.CharField(max_length=255, default='unknown') 
    result         = models.CharField(max_length=50, default='SUCCESS')  
    ip_address     = models.GenericIPAddressField(null=True)
    device_id_hash = models.CharField(max_length=64, blank=True) 
    metadata       = models.JSONField(default=dict)
    created_at     = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        db_table      = 'officer_activities'
        ordering      = ['-created_at']
        indexes = [
            models.Index(fields=['created_at']),
            models.Index(fields=['severity', 'created_at']),
            models.Index(fields=['pno']),
        ]

    def save(self, *args, **kwargs):
        if self.pk:
            raise PermissionError("Audit logs are immutable and cannot be updated.")
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        raise PermissionError("Audit logs cannot be deleted.")

class OverrideRequest(models.Model):
    STATUS_CHOICES = [
        ('PENDING', 'Pending ADMIN Approval'),
        ('APPROVED', 'Approved'),
        ('REJECTED', 'Rejected'),
        ('EXPIRED', 'Expired'),
    ]
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    unit = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    pilot              = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='overrides_initiated')
    target_user        = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='overrides_target')
    commanding_officer = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, blank=True, related_name='overrides_ratified')
    field          = models.CharField(max_length=100)
    previous_value = models.TextField(blank=True)
    proposed_value = models.TextField()
    reason         = models.TextField()
    status       = models.CharField(max_length=20, choices=STATUS_CHOICES, default='PENDING', db_index=True)
    created_at   = models.DateTimeField(auto_now_add=True, db_index=True)
    resolved_at  = models.DateTimeField(null=True, blank=True)
    expires_at   = models.DateTimeField(null=True, blank=True)
    device_fingerprint = models.CharField(max_length=255, blank=True)
    integrity_state    = models.CharField(max_length=50, default='UNKNOWN')

    class Meta:
        db_table = 'override_requests'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['status', 'created_at']),
        ]

class DraftChange(models.Model):
    STATUS_CHOICES = [
        ('DRAFT', 'Draft'),
        ('SUBMITTED', 'Submitted'),
        ('UNDER_REVIEW', 'Under Review'),
        ('APPROVED', 'Approved'),
        ('APPLYING', 'Applying'),
        ('APPLIED', 'Applied'),
        ('REJECTED', 'Rejected'),
        ('EXPIRED', 'Expired'),
        ('CONFLICTED', 'Conflicted'),
        ('FAILED', 'Failed'),
        ('WITHDRAWN', 'Withdrawn'),
    ]
    actor       = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='changes_made')
    target      = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='changes_received')
    unit        = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    model       = models.CharField(max_length=100)
    field       = models.CharField(max_length=100)
    old_value   = models.JSONField(null=True)
    new_value   = models.JSONField()
    status      = models.CharField(max_length=20, choices=STATUS_CHOICES, default='DRAFT', db_index=True)
    override_request = models.OneToOneField(OverrideRequest, on_delete=models.SET_NULL, null=True, blank=True)
    expected_state = models.JSONField(null=True, blank=True)
    approver    = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, blank=True, related_name='changes_approved')
    approved_at = models.DateTimeField(null=True, blank=True)
    rejecter    = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True, blank=True, related_name='changes_rejected')
    rejected_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    applied_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'draft_changes'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['status', 'created_at']),
        ]

class AdversarialSignal(models.Model):
    detector_type    = models.CharField(max_length=50)
    description      = models.TextField()
    affected_actors  = models.JSONField()
    confidence_score = models.IntegerField()
    created_at       = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'adversarial_signals'

class InvariantViolation(models.Model):
    invariant_key    = models.CharField(max_length=100)
    description      = models.TextField()
    technical_trace  = models.JSONField()
    severity         = models.CharField(max_length=20, default='CRITICAL')
    created_at       = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'invariant_violations'
