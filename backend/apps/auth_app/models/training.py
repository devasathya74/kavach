from django.db import models
import uuid

class TrainingModule(models.Model):
    id      = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    unit    = models.ForeignKey('UnitMaster', on_delete=models.PROTECT)
    title   = models.CharField(max_length=200)
    description = models.TextField()
    video_url = models.URLField(blank=True)
    document  = models.FileField(upload_to='training_docs/', blank=True)
    is_mandatory = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    class Meta:
        db_table = 'training_modules'
        ordering = ['-created_at']

class TrainingAcknowledgment(models.Model):
    module    = models.ForeignKey(TrainingModule, on_delete=models.CASCADE, related_name='acknowledgments')
    officer   = models.ForeignKey('Officer', on_delete=models.CASCADE)
    is_completed = models.BooleanField(default=False)
    completed_at = models.DateTimeField(null=True, blank=True)
    device_id = models.CharField(max_length=255, blank=True)
    class Meta:
        db_table = 'training_acknowledgments'
        unique_together = ['module', 'officer']
