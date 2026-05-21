from django.db import models
import uuid

class Incident(models.Model):
    STATUS_CHOICES = [
        ('REPORTED', 'Reported'),
        ('INVESTIGATING', 'Investigating'),
        ('RESOLVED', 'Resolved'),
        ('FALSE_POSITIVE', 'False Positive'),
        ('ARCHIVED', 'Archived'),
    ]
    SEVERITY_CHOICES = [
        ('LOW', 'Low'),
        ('MEDIUM', 'Medium'),
        ('HIGH', 'High'),
        ('CRITICAL', 'Critical'),
    ]
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    incident_id = models.CharField(max_length=20, unique=True)
    type    = models.CharField(max_length=50)
    title   = models.CharField(max_length=200)
    summary = models.TextField()
    severity = models.CharField(max_length=10, choices=SEVERITY_CHOICES, default='MEDIUM')
    status   = models.CharField(max_length=20, choices=STATUS_CHOICES, default='REPORTED')
    reported_by = models.ForeignKey('Officer', on_delete=models.PROTECT, related_name='incidents_reported')
    unit        = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    latitude  = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    longitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    location_name = models.CharField(max_length=255, blank=True)
    occurred_at = models.DateTimeField()
    reported_at = models.DateTimeField(auto_now_add=True)
    linked_officers = models.ManyToManyField('Officer', blank=True, related_name='linked_incidents')
    linked_devices  = models.ManyToManyField('OfficerDevice', blank=True, related_name='linked_incidents')
    evidence_manifest = models.JSONField(default=dict, blank=True) 
    metadata = models.JSONField(default=dict, blank=True)
    def save(self, *args, **kwargs):
        super().save(*args, **kwargs)
    class Meta:
        db_table = 'incidents'
        ordering = ['-reported_at']

class IncidentEvent(models.Model):
    EVENT_TYPES = [
        ('CREATED', 'Incident Created'),
        ('STATUS_CHANGED', 'Status Changed'),
        ('SEVERITY_CHANGED', 'Severity Changed'),
        ('EVIDENCE_ADDED', 'Evidence Added'),
        ('OFFICER_LINKED', 'Officer Linked'),
        ('ESCALATED', 'Escalated'),
        ('RESOLVED', 'Resolved'),
    ]
    incident    = models.ForeignKey(Incident, on_delete=models.CASCADE, related_name='timeline')
    event_type  = models.CharField(max_length=50, choices=EVENT_TYPES)
    actor       = models.ForeignKey('Officer', on_delete=models.SET_NULL, null=True)
    payload     = models.JSONField(default=dict)
    timestamp   = models.DateTimeField(auto_now_add=True)
    trace_id    = models.CharField(max_length=100, blank=True)
    class Meta:
        db_table = 'incident_timeline'
        ordering = ['timestamp']

class FieldData(models.Model):
    CATEGORY_CHOICES = [
        ('EVIDENCE', 'Forensic Evidence'),
        ('DOCUMENT', 'Official Document'),
        ('MAP', 'Operational Map'),
        ('INTEL', 'Intelligence Report'),
    ]
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    unit    = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    uploader = models.ForeignKey('Officer', on_delete=models.PROTECT)
    title   = models.CharField(max_length=200)
    file    = models.FileField(upload_to='field_data/%Y/%m/%d/')
    category = models.CharField(max_length=20, choices=CATEGORY_CHOICES, default='DOCUMENT')
    tags    = models.JSONField(default=list, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    sha256     = models.CharField(max_length=64, blank=True)
    size_bytes = models.BigIntegerField(default=0)
    metadata   = models.JSONField(default=dict, blank=True)
    class Meta:
        db_table = 'field_data'
        ordering = ['-created_at']
