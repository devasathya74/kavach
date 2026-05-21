import uuid
from django.db import models
from apps.auth_app.models import Officer

class Order(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    title = models.CharField(max_length=255)
    content = models.TextField(blank=True, null=True)
    image_path = models.CharField(max_length=500, blank=True, null=True)
    is_mandatory = models.BooleanField(default=True)
    issued_by = models.CharField(max_length=100, default='ADMIN')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'orders'
        ordering = ['-created_at']

class OrderAck(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(Officer, on_delete=models.CASCADE)
    order = models.ForeignKey(Order, on_delete=models.CASCADE)
    device_id = models.CharField(max_length=255)
    read_duration_ms = models.BigIntegerField(default=0)
    idempotency_key = models.CharField(max_length=255, unique=True, null=True, blank=True)
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'order_ack'
        unique_together = ('user', 'order')

class Alert(models.Model):
    ALERT_TYPES = [
        ('NORMAL', 'Normal'),
        ('PRIORITY', 'Priority'),
        ('CRITICAL', 'Critical'),
    ]
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    type = models.CharField(max_length=20, choices=ALERT_TYPES, default='NORMAL')
    title = models.CharField(max_length=255)
    content = models.TextField()
    target_unit = models.CharField(max_length=100, blank=True, null=True) # null means ALL
    created_at = models.DateTimeField(auto_now_add=True)
    created_by = models.CharField(max_length=100, default='ADMIN')

    class Meta:
        db_table = 'alerts'
        ordering = ['-created_at']

class AlertAck(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(Officer, on_delete=models.CASCADE)
    alert = models.ForeignKey(Alert, on_delete=models.CASCADE)
    read_time = models.IntegerField(default=0) # in seconds
    is_verified = models.BooleanField(default=False)
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'alert_ack'
        unique_together = ('user', 'alert')
