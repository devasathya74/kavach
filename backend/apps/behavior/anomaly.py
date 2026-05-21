from .models import BehaviorEvent, DisciplineScore
from apps.auth_app.models import NotificationLog
from django.db.models import Avg
from django.utils import timezone
from datetime import timedelta
from .ml_engine import AnomalyMLDetector
import numpy as np

class AnomalyEngine:
    """
    Explainable Rule-based Adaptive AI for KAVACH.
    """

    def __init__(self, officer):
        self.officer = officer
        self.suspicion_score = 0
        self.reasons = []
        self.feature_names = [
            "read_time_ratio", "scroll_speed", "quiz_attempts", 
            "ack_delay", "session_duration", "baseline_deviation"
        ]
        self.long_term_baseline = 100
        self.short_term_baseline = 100

    def is_monitored_rank(self):
        """
        Only PC, HC, and Constables are tracked for anomalies.
        CO, DC, AC, QM, CC, SM are command level.
        """
        from apps.auth_app.models import Officer
        MONITORED = [Officer.Rank.PC, Officer.Rank.HC, Officer.Rank.CONSTABLE]
        return self.officer.rank in MONITORED

    def evaluate_all_patterns(self, metadata=None):
        if not self.is_monitored_rank():
            return 

        # 0. Input Trust Layer (Trust Before Intelligence)
        from .input_trust import InputTrustEngine
        confidence = InputTrustEngine.validate_event(self.officer, "behavior_batch", metadata or {})
        
        if confidence < 0.5:
             self.suspicion_score += 20
             self.reasons.append("🚩 Data Integrity Failure: Signal suspected as spoofed/faked.")
             if confidence == 0.0: return # Hard drop blatant lies
        
        # 1. Dual Baseline Isolation (7d vs 30d Reference)
        ds, _ = DisciplineScore.objects.get_or_create(officer=self.officer)
        history_30 = ds.reputation_history[-30:]
        history_7  = ds.reputation_history[-7:]
        
        self.long_term_baseline = sum(h['score'] for h in history_30) / len(history_30) if history_30 else 100
        self.short_term_baseline = sum(h['score'] for h in history_7) / len(history_7) if history_7 else 100
        
        # Detection of Baseline Poisoning: If short-term is drifting away from long-term
        if abs(self.short_term_baseline - self.long_term_baseline) > 15:
             self.suspicion_score += 10
             self.reasons.append("🚩 Hidden Drift detected (Dual Baseline Anomaly).")

        # 1. Trajectory Prediction (Next 3 Days)
        # Using simple linear momentum for now
        if len(history_7) >= 3:
            deltas = [history_7[i]['score'] - history_7[i-1]['score'] for i in range(1, len(history_7))]
            momentum = sum(deltas) / len(deltas)
            ds.predicted_score = int(max(0, min(100, ds.score + (momentum * 3))))
            
            if ds.predicted_score < 40 and ds.score >= 40:
                self.suspicion_score += 15
                self.reasons.append("🔮 Preemptive Alert: Predicted score crossing RESTRICTED threshold in 3 days.")
                ds.trajectory_status = 'FALLING'
            elif ds.predicted_score > 80:
                ds.trajectory_status = 'RISING'

        self.check_fast_reading()
        self.check_quiz_gaming()
        self.check_app_background_abuse()
        self.check_notification_ignore_pattern()
        
        # 4. Suspicion Cap: Prevent single-event explosion
        self.suspicion_score = min(60, self.suspicion_score)
        
        self.update_risk_level(ds)

    def check_fast_reading(self):
        # Rule 1: Fast Reading
        recent_completes = BehaviorEvent.objects.filter(officer=self.officer, event_type='TRAINING_COMPLETE').order_by('-received_at')[:3]
        for event in recent_completes:
            actual = event.metadata.get('duration_seconds', 0)
            expected = event.metadata.get('expected_seconds', 0)
            if expected > 0 and actual < expected * 0.6:
                self.suspicion_score += 40
                self.reasons.append("बहुत तेज़ पढ़ने की कोशिश (Fast Reading Attempt)")

    def check_quiz_gaming(self):
        # Rule 2: Quiz Gaming
        recent_quizzes = BehaviorEvent.objects.filter(officer=self.officer, event_type='QUIZ_ATTEMPT').order_by('-received_at')[:10]
        t_attempts = {}
        for q in recent_quizzes:
            tid = q.training_id
            t_attempts[tid] = t_attempts.get(tid, 0) + 1
            if t_attempts[tid] > 2:
                self.suspicion_score += 20
                self.reasons.append("क्विज़ में बार-बार तुक्का लगाना (Quiz Gaming Pattern)")
                break

    def check_app_background_abuse(self):
        # Rule 3: Backgrounding
        bg_count = BehaviorEvent.objects.filter(officer=self.officer, event_type='APP_BACKGROUND', received_at__gt=timezone.now() - timedelta(hours=24)).count()
        if bg_count > 5:
            self.suspicion_score += 25
            self.reasons.append(f"ट्रेनिंग के दौरान बार-बार ऐप बंद करना (App Background Abuse: {bg_count} times)")

    def check_notification_ignore_pattern(self):
        # Rule 4: Delayed ACKs (Adaptive)
        from apps.monitoring.adaptive import AdaptiveEngine
        adaptive = AdaptiveEngine()
        dynamic_limit = adaptive.get_dynamic_ack_threshold()
        
        late_acks = NotificationLog.objects.filter(officer=self.officer, acked_at__isnull=False, created_at__gt=timezone.now() - timedelta(days=7))
        count = 0
        for log in late_acks:
            if (log.acked_at - log.created_at).total_seconds() > dynamic_limit: 
                count += 1
        if count >= 3:
            self.suspicion_score += 30
            self.reasons.append(f"आदेशों को स्वीकार करने में बार-बार देरी (Adaptive Limit: {int(dynamic_limit)}s)")

    def update_risk_level(self, score_obj):
        rule_score = min(100, self.suspicion_score)
        
        # 2. ML Prediction & Explanation
        detector = AnomalyMLDetector()
        ml_input = [0.8, 1.0, 0, 50, 0, 0] # Simplified input
        ml_raw_score = detector.predict_anomaly_score(ml_input)
        
        # Map ML score
        ml_mapped_score = max(0, min(100, 50 - (ml_raw_score * 100)))

        # 3. Explain ML Flag (Feature Contribution)
        if detector.model and ml_raw_score < -0.1:
            # Simple contribution: compare with a "normal" baseline [1.0, 1.0, 0, 10, 0, 0]
            baseline = np.array([1.0, 1.0, 0, 10, 0, 0])
            deviations = np.abs(np.array(ml_input) - baseline)
            top_idx = deviations.argsort()[-1] # Get top contributor
            self.reasons.append(f"असामान्य व्यवहार पैटर्न: {self.feature_names[top_idx]}")

        # 4. Final Hybrid Score
        final_anomaly_score = int((rule_score * 0.6) + (ml_mapped_score * 0.4))
        
        # 5. Stricter Risk Classification (Confidence Gate)
        if final_anomaly_score >= 70 and confidence >= 0.75: 
            risk = 'HIGH_RISK'
        elif final_anomaly_score >= 35: 
            risk = 'SUSPICIOUS'
        else: 
            risk = 'NORMAL'

        # 6. Confidence Score
        confidence = round(min(final_anomaly_score / 100, 1.0), 2)

        # 7. Trust Level Tier (The Privilege Control)
        # We use a mix of final_anomaly_score and base discipline score
        total_score = score_obj.score # This is the 0-100 base score
        
        if total_score >= 80:
            score_obj.trust_level = DisciplineScore.TrustLevel.TRUSTED
        elif total_score >= 60:
            score_obj.trust_level = DisciplineScore.TrustLevel.NORMAL
        elif total_score >= 40:
            score_obj.trust_level = DisciplineScore.TrustLevel.WATCHLIST
        else:
            score_obj.trust_level = DisciplineScore.TrustLevel.RESTRICTED
            score_obj.enforcement_active = True
            score_obj.enforcement_reason = " | ".join(self.reasons)

        score_obj.risk_level = risk
        score_obj.anomaly_score = final_anomaly_score
        score_obj.anomaly_reasons = list(set(self.reasons))
        
        # 7. Signal Arbitration (The Central Brain)
        from .decision_core import DecisionCore
        signals = {
            'ai_anomaly': 100 - self.suspicion_score,
            'human_review': score_obj.score,
            'baseline_drift': int(self.long_term_baseline),
            'prediction': getattr(score_obj, 'predicted_score', 100),
            'confidence': confidence
        }
        
        # Arbitrated Final Score
        final_arbitrated_score = DecisionCore.arbitrate(self.officer, signals)
        score_obj.score = final_arbitrated_score

        # 8. Trust Level Tier (The Privilege Control)
        total_score = score_obj.score 
        
        if total_score >= 80:
            score_obj.trust_level = DisciplineScore.TrustLevel.TRUSTED
        elif total_score >= 60:
            score_obj.trust_level = DisciplineScore.TrustLevel.NORMAL
        elif total_score >= 40:
            score_obj.trust_level = DisciplineScore.TrustLevel.WATCHLIST
        else:
            score_obj.trust_level = DisciplineScore.TrustLevel.RESTRICTED
            score_obj.enforcement_active = True
            score_obj.enforcement_reason = " | ".join(self.reasons)

        score_obj.risk_level = risk
        score_obj.anomaly_score = final_anomaly_score
        score_obj.anomaly_reasons = list(set(self.reasons))
        score_obj.save()

        if risk == 'HIGH_RISK':
            score_obj.requires_review = True
            score_obj.save()
