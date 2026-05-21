from django.db import models
from django.conf import settings
from django.utils import timezone
from datetime import timedelta

class ViolationPrediction(models.Model):
    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='predictions')
    prediction_score = models.FloatField() # 0.0 to 1.0
    risk_status = models.CharField(max_length=20) # WATCH, LIKELY_VIOLATION, IMMINENT_FAILURE
    reasons = models.JSONField(default=list)
    confidence = models.FloatField(default=0.0)
    
    window_start = models.DateTimeField(auto_now_add=True)
    window_end = models.DateTimeField()
    
    is_expired = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'violation_predictions'
        ordering = ['-created_at']

    def save(self, *args, **kwargs):
        if not self.window_end:
            self.window_end = timezone.now() + timedelta(hours=24)
        super().save(*args, **kwargs)

    @property
    def is_active(self):
        return timezone.now() < self.window_end and not self.is_expired
