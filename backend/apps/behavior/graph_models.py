from django.db import models
from django.conf import settings

class BehavioralCluster(models.Model):
    name = models.CharField(max_length=100)
    risk_level = models.CharField(max_length=20, default='NORMAL') # NORMAL, SUSPICIOUS, HIGH_RISK
    avg_anomaly_score = models.FloatField(default=0)
    common_patterns = models.JSONField(default=list) # e.g. ["SAME_IP", "SAME_ACK_DELAY"]
    created_at = models.DateTimeField(auto_now_add=True)
    last_detected = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'behavioral_clusters'

class ClusterMembership(models.Model):
    cluster = models.ForeignKey(BehavioralCluster, on_delete=models.CASCADE, related_name='members')
    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    similarity_contribution = models.FloatField(default=0)

    class Meta:
        db_table = 'cluster_memberships'
        unique_together = ('cluster', 'officer')

class SimilarityEdge(models.Model):
    user_a = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='edges_a')
    user_b = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='edges_b')
    score = models.FloatField() # 0.0 to 1.0
    common_features = models.JSONField(default=list)
    detected_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'similarity_edges'
        unique_together = ('user_a', 'user_b')
