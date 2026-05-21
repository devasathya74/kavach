from django.db import models
from django.conf import settings

class ChainBreakAlert(models.Model):
    leader = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='led_breaks')
    followers_count = models.IntegerField()
    cascade_score = models.FloatField() # 0.0 to 1.0
    pattern_desc = models.TextField()
    is_investigated = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'chain_break_alerts'
        ordering = ['-created_at']

    def __str__(self):
        return f"Chain Break Alert for {self.leader.pno} ({self.cascade_score})"
