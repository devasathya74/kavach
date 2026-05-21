from django.db import models
from django.conf import settings

class CommandSuggestion(models.Model):
    SUGGESTION_TYPES = [
        ('REVIEW_REQUIRED', 'Review Required'),
        ('WARNING', 'Issue Warning'),
        ('RESTRICTION', 'Temporary Restriction'),
        ('OBSERVATION', 'Extended Observation'),
        ('NO_ACTION', 'No Action Needed'),
    ]

    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='command_suggestions')
    suggestion_type = models.CharField(max_length=50, choices=SUGGESTION_TYPES)
    score = models.FloatField() # 0.0 to 1.0
    reasons = models.JSONField(default=list)
    confidence = models.FloatField(default=0.0)
    
    is_processed = models.BooleanField(default=False)
    admin_action = models.CharField(max_length=50, null=True, blank=True) # APPROVED, REJECTED, MODIFIED
    action_taken = models.CharField(max_length=100, null=True, blank=True)
    
    created_at = models.DateTimeField(auto_now_add=True)
    processed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'command_suggestions'
        ordering = ['-created_at']

    def __str__(self):
        return f"Suggestion for {self.officer.pno}: {self.suggestion_type} ({self.score})"
