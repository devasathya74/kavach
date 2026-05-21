from django.db import models
from django.conf import settings

class DecisionAudit(models.Model):
    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='audited_decisions')
    action_type = models.CharField(max_length=50) # WARNING, REVIEW, RESTRICTION
    
    # Behavior Snapshots
    pre_score = models.FloatField()
    post_score = models.FloatField(null=True, blank=True)
    
    effectiveness_score = models.FloatField(null=True, blank=True) # 0.0 to 1.0
    status = models.CharField(max_length=20, default='PENDING') # PENDING, EFFECTIVE, NEUTRAL, INEFFECTIVE
    
    created_at = models.DateTimeField(auto_now_add=True)
    evaluated_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'decision_audits'
        ordering = ['-created_at']

    def __str__(self):
        return f"Audit for {self.officer.pno}: {self.action_type} ({self.status})"
