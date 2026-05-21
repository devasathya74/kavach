from django.db import models

class AlertRule(models.Model):
    name = models.CharField(max_length=100)
    metric = models.CharField(max_length=50)  # delivery_delay, cpu_usage, memory_usage
    threshold = models.FloatField()
    condition = models.CharField(max_length=10, default='>')  # >, <, ==
    severity = models.CharField(max_length=10, default='CRITICAL')

class SystemAlert(models.Model):
    rule = models.ForeignKey(AlertRule, on_delete=models.CASCADE)
    message = models.TextField()
    is_resolved = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

class SystemState(models.Model):
    current_adaptive_factor = models.FloatField(default=0.0) # 0.0 to 1.0
    network_delay_sec = models.FloatField(default=0.0)
    failure_rate = models.FloatField(default=0.0)
    cpu_load = models.FloatField(default=0.0)
    
    adaptive_mode = models.BooleanField(default=True)
    last_updated = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'system_state'

    def __str__(self):
        return f"System State: Factor {self.current_adaptive_factor:.2f}"
