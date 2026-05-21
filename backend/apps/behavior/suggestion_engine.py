from .models import DisciplineScore, Violation
from .prediction_models import ViolationPrediction
from .graph_models import ClusterMembership
from .suggestion_models import CommandSuggestion

class SuggestionEngine:
    def __init__(self, officer):
        self.officer = officer

    def generate_suggestion(self):
        score = 0
        reasons = []

        # 1. Discipline Score (Inv: lower is worse)
        ds = DisciplineScore.objects.filter(officer=self.officer).first()
        if ds:
            # Scale: 100 -> 0, 0 -> 1.0
            ds_weight = (100 - ds.score) / 100.0
            score += ds_weight * 0.25
            if ds.score < 50: reasons.append("Low discipline score (<50)")

        # 2. Violation History
        v_count = Violation.objects.filter(officer=self.officer).count()
        if v_count > 0:
            score += min(0.20, v_count * 0.05)
            reasons.append(f"History of violations ({v_count})")

        # 3. Prediction Risk
        pred = ViolationPrediction.objects.filter(officer=self.officer).order_by('-created_at').first()
        if pred and pred.is_active:
            score += pred.prediction_score * 0.20
            reasons.append(f"Prediction signal: {pred.risk_status}")

        # 4. Anomaly Flags
        if ds and ds.anomaly_score > 60:
            score += 0.20
            reasons.extend(ds.anomaly_reasons[:2])

        # 5. Cluster Risk
        cluster = ClusterMembership.objects.filter(officer=self.officer).first()
        if cluster and cluster.cluster.risk_level == 'HIGH_RISK':
            score += 0.15
            reasons.append("Coordinated high-risk cluster membership")

        # Final Classification
        s_type = 'NO_ACTION'
        if score > 0.8: s_type = 'REVIEW_REQUIRED'
        elif score > 0.6: s_type = 'WARNING'
        elif score > 0.4: s_type = 'OBSERVATION'

        if s_type != 'NO_ACTION':
            CommandSuggestion.objects.create(
                officer=self.officer,
                suggestion_type=s_type,
                score=round(score, 2),
                reasons=reasons,
                confidence=round(score * 0.85, 2)
            )
        
        return s_type, score
