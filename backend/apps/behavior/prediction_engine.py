from django.utils import timezone
from datetime import timedelta
from .models import DisciplineScore, BehaviorEvent, Violation
from .prediction_models import ViolationPrediction
from .graph_models import ClusterMembership

class PredictionEngine:
    def __init__(self, officer):
        self.officer = officer

    def run_prediction(self):
        score = 0
        reasons = []

        # 1. Historical Behavior Drift (Trend analysis)
        # Compare last 24h with previous 6 days
        now = timezone.now()
        last_24h = BehaviorEvent.objects.filter(officer=self.officer, received_at__gt=now - timedelta(days=1)).count()
        prev_avg = BehaviorEvent.objects.filter(officer=self.officer, received_at__gt=now - timedelta(days=7), received_at__lt=now - timedelta(days=1)).count() / 6
        
        if last_24h < prev_avg * 0.5:
            score += 0.35
            reasons.append("Significant drop in recent activity/engagement")

        # 2. Recent Violations (Warm-up to a failure)
        recent_violations = Violation.objects.filter(officer=self.officer, created_at__gt=now - timedelta(hours=24)).count()
        if recent_violations > 0:
            score += 0.30
            reasons.append(f"Recent violations detected ({recent_violations}) in last 24h")

        # 3. Cluster Influence Pressure
        cluster_membership = ClusterMembership.objects.filter(officer=self.officer).first()
        if cluster_membership and cluster_membership.cluster.risk_level == 'HIGH_RISK':
            score += 0.20
            reasons.append("High-risk cluster influence detected")

        # 4. Discipline Score Decay
        ds = DisciplineScore.objects.filter(officer=self.officer).first()
        if ds and ds.anomaly_score > 50:
            score += 0.15
            reasons.append("Elevated anomaly score trend")

        # Risk Classification
        status = "STABLE"
        if score >= 0.8: status = "IMMINENT_FAILURE"
        elif score >= 0.6: status = "LIKELY_VIOLATION"
        elif score >= 0.3: status = "WATCH"

        if score > 0.3:
            # Update or create prediction
            ViolationPrediction.objects.update_or_create(
                officer=self.officer,
                defaults={
                    'prediction_score': round(score, 2),
                    'risk_status': status,
                    'reasons': reasons,
                    'confidence': round(score * 0.9, 2), # Simplified confidence
                    'window_end': now + timedelta(hours=24)
                }
            )
        
        return score, status
