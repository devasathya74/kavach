from django.db import models
import uuid

class RealtimeSession(models.Model):
    officer    = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='active_sessions')
    device_id  = models.CharField(max_length=255)
    socket_id  = models.CharField(max_length=100, unique=True)
    connected_at = models.DateTimeField(auto_now_add=True)
    last_heartbeat = models.DateTimeField(auto_now=True)
    app_version = models.CharField(max_length=20, blank=True)
    system_mode = models.CharField(max_length=20, default='NORMAL')
    class Meta:
        db_table = 'realtime_sessions'

class NotificationDelivery(models.Model):
    PRIORITY_LEVELS = [
        ('SILENT', 'Silent Sync'),
        ('INFO', 'Passive Info'),
        ('IMPORTANT', 'Realtime In-App'),
        ('URGENT', 'Immediate Notification'),
        ('CRITICAL', 'Broadcast + Siren + Push'),
    ]
    CHANNEL_CHOICES = [
        ('WEBSOCKET', 'WebSocket Gateway'),
        ('FCM_PUSH', 'FCM Push Notification'),
        ('SMS', 'Emergency SMS'),
    ]
    STATUS_CHOICES = [
        ('QUEUED', 'Queued'),
        ('SENT', 'Sent'),
        ('DELIVERED', 'Delivered/Acked'),
        ('FAILED', 'Failed'),
        ('RETRYING', 'Retrying'),
    ]
    id       = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    event    = models.ForeignKey('OperationalEvent', on_delete=models.CASCADE, related_name='deliveries')
    target   = models.ForeignKey('Officer', on_delete=models.CASCADE, related_name='notifications_received')
    priority = models.CharField(max_length=20, choices=PRIORITY_LEVELS, default='INFO')
    channel  = models.CharField(max_length=20, choices=CHANNEL_CHOICES)
    status   = models.CharField(max_length=20, choices=STATUS_CHOICES, default='QUEUED')
    retry_count = models.IntegerField(default=0)
    created_at   = models.DateTimeField(auto_now_add=True)
    delivered_at = models.DateTimeField(null=True, blank=True)
    trace_id = models.CharField(max_length=100, db_index=True)
    class Meta:
        db_table = 'notification_deliveries'
        ordering = ['-created_at']

class NotificationLog(models.Model):
    officer    = models.ForeignKey('Officer', on_delete=models.CASCADE)
    pno        = models.CharField(max_length=20)
    title      = models.CharField(max_length=200)
    acked_at   = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    class Meta:
        db_table = 'notification_logs'
