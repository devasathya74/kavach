from django.db import models
from django.conf import settings

class CommanderReview(models.Model):
    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='received_reviews')
    reviewer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='given_reviews')
    
    rating = models.IntegerField() # -10 to +10
    reason = models.TextField()
    
    # Forensic Audit
    previous_score = models.IntegerField(default=0)
    new_score      = models.IntegerField(default=0)
    delta_score    = models.IntegerField(default=0)
    
    # Distributed Command
    system_validation_pass = models.BooleanField(default=True)
    approvals_needed = models.IntegerField(default=1) # 2 for high impact
    approvals_count  = models.IntegerField(default=0)
    
    # Workflow
    STATUS_CHOICES = [
        ('PENDING', 'Pending CO Approval'),
        ('APPROVED', 'Approved & Applied'),
        ('REJECTED', 'Rejected'),
    ]
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='APPROVED')
    
    is_valid = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'commander_reviews'
        ordering = ['-created_at']

    def __str__(self):
        return f"Review for {self.officer.pno} by {self.reviewer.pno} ({self.rating})"
