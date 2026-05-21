from django.db import models
import uuid

class Broadcast(models.Model):
    PRIORITY_CHOICES = [
        ('SILENT', 'Silent Sync'),
        ('INFO', 'Passive Info'),
        ('IMPORTANT', 'Important Notice'),
        ('URGENT', 'Urgent Alert'),
        ('CRITICAL', 'Mission Critical Broadcast'),
    ]
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    actor   = models.ForeignKey('Officer', on_delete=models.PROTECT, related_name='broadcasts_sent')
    unit    = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    title   = models.CharField(max_length=200)
    content = models.TextField()
    image_url = models.URLField(max_length=1000, blank=True, null=True)
    priority = models.CharField(max_length=20, choices=PRIORITY_CHOICES, default='INFO')
    targeted_officers = models.ManyToManyField('Officer', related_name='targeted_broadcasts', blank=True)
    broadcast_type = models.CharField(max_length=50, default='TEXT') # TEXT, IMAGE, ORDER
    expires_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    trace_id   = models.CharField(max_length=100, db_index=True)
    metadata = models.JSONField(default=dict, blank=True)
    class Meta:
        db_table = 'broadcasts'
        ordering = ['-created_at']

class BroadcastAcknowledgment(models.Model):
    broadcast = models.ForeignKey(Broadcast, on_delete=models.CASCADE, related_name='acknowledgments')
    officer   = models.ForeignKey('Officer', on_delete=models.CASCADE)
    acked_at  = models.DateTimeField(auto_now_add=True)
    device_id = models.CharField(max_length=255, blank=True)
    class Meta:
        db_table = 'broadcast_acknowledgments'
        unique_together = ['broadcast', 'officer']

class BroadcastRecipient(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    broadcast = models.ForeignKey(Broadcast, on_delete=models.CASCADE, related_name='recipients')
    user_id = models.CharField(max_length=255, db_index=True)
    pno = models.CharField(max_length=100)
    unit = models.CharField(max_length=200, blank=True)
    company = models.CharField(max_length=200, blank=True)
    
    # Delivery intelligence states
    status = models.CharField(max_length=50, default='QUEUED') # QUEUED, SENT, DELIVERED, READ, ACKNOWLEDGED, FAILED
    delivered_at = models.DateTimeField(null=True, blank=True)
    read_at = models.DateTimeField(null=True, blank=True)
    acked_at = models.DateTimeField(null=True, blank=True)
    failed_at = models.DateTimeField(null=True, blank=True)
    failure_reason = models.TextField(blank=True)

    class Meta:
        db_table = 'broadcast_recipients'
        unique_together = ['broadcast', 'user_id']

class BroadcastAttachment(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    broadcast = models.ForeignKey(Broadcast, on_delete=models.CASCADE, related_name='attachments')
    file_name = models.CharField(max_length=255)
    mime_type = models.CharField(max_length=100)
    file_size = models.BigIntegerField(default=0)
    remote_url = models.URLField(max_length=1000)
    checksum = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'broadcast_attachments'

class BroadcastDispatchJob(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    broadcast = models.OneToOneField(Broadcast, on_delete=models.CASCADE, related_name='dispatch_job')
    status = models.CharField(max_length=50, default='PENDING') # PENDING, PROCESSING, COMPLETED, FAILED
    retry_count = models.IntegerField(default=0)
    last_attempt_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    error_log = models.TextField(blank=True)

    class Meta:
        db_table = 'broadcast_dispatch_jobs'
