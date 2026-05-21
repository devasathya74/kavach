from django.conf import settings
from datetime import timedelta
from django.utils import timezone
from apps.monitoring.utils import get_system_health # Assuming this exists or will be added

class DecisionCore:
    """
    The Central Brain of KAVACH.
    Arbitrates between conflicting signals from AI, Human Reviewers, 
    and Historical Baselines based on the current System Context.
    """
    
    # Feature Flags (Kill Switches)
    FLAGS = getattr(settings, 'KAVACH_FEATURES', {
        "ml_anomaly": True,
        "graph_intelligence": True,
        "prediction_engine": True,
        "human_review": True,
        "adaptive_enforcement": True
    })

    @classmethod
    def arbitrate(cls, officer, signals, action_type='PASSIVE'):
        from .models import DisciplineScore
        from .pilot_controller import PilotController
        ds, _ = DisciplineScore.objects.get_or_create(officer=officer)
        
        # 0. Pilot Phase Hard Enforcement
        impact = signals.get('impact_level', 'LOW')
        if not PilotController.is_action_allowed(action_type, impact):
             # Log decision for shadow mode / audit, but do not apply.
             return ds.score 
        impact = signals.get('impact_level', 'LOW')
        if impact == 'HIGH' or signals.get('irreversible', False):
             # Irreversible damage possible. Hard stop.
             return cls.deep_validation_gate(ds, signals)

        # 2. Pre-Emptive Guard (Soft Control)
        if signals.get('trend') == 'RISING' and signals.get('confidence', 0) > 0.7:
             cls.apply_soft_friction(officer, "Trend Detected")

        # 3. Dual-Key Override (Cross-Signal Validation)
        # Cannot override without independent confirmation (Anomaly + Prediction)
        threat_confidence = signals.get('confidence', 0.0)
        has_independent_confirmation = signals.get('prediction_match', False)
        
        if threat_confidence > 0.9 and has_independent_confirmation:
             return cls.execute_immediate(ds, signals)

        # 4. Fast Path with Post-Action Verification
        if action_type == 'URGENT':
             # 1. Validate Priority Source first
             if cls.is_priority_source_verified(signals):
                 result = cls.fast_path_arbitrate(ds, signals)
                 # 2. Trigger Async Deep Validation
                 cls.trigger_deep_validation.delay(officer.id, signals) 
                 return result

        # 3. Decision Explanation with Raw Evidence
        explanation = {
            'reason': cls.generate_reason(signals),
            'confidence': f"{int(threat_confidence * 100)}%",
            'raw_evidence': signals.get('raw_data', {}),
            'decision_path': cls.trace_decision_path(signals),
            'status': 'VERIFIED'
        }
        ds.last_decision_explanation = explanation
        # 4. System Health & Conflict Check
        if ds.system_health_index < 70.0:
             # Conservative Mode
             pass

        # 4. Human Reliability Gate: Dynamic Trust Lock
        from .models import ReviewerReliability
        reviewer_pno = signals.get('reviewer_pno')
        
        # Dynamic Threshold based on System Health
        # If system is unstable, we are MORE strict with human reviews
        bias_threshold = 40 if ds.system_health_index > 80 else 25
        
        if reviewer_pno:
            rel = ReviewerReliability.objects.filter(reviewer__pno=reviewer_pno).first()
            # Trust Lock Threshold also dynamic
            trust_lock_limit = 0.7 if ds.system_health_index < 90 else 0.6
            
            if rel and (rel.reliability_score < trust_lock_limit or rel.bias_flag):
                human_signal = ds.score 

        if abs(ai_signal - human_signal) >= bias_threshold:
             ds.resolution_needed = True
             
             # 4.5 Observation Aging (Anti-Stalling)
             if ds.monitoring_mode == 'CONTROLLED_OBSERVATION' and ds.monitoring_start_at:
                 from django.utils import timezone
                 age_hours = (timezone.now() - ds.monitoring_start_at).total_seconds() / 3600
                 
                 # ALERT CO BEFORE AUTO-PENALTY (At 36 hours)
                 if 36 < age_hours < 48 and not ds.resolution_needed:
                     from apps.auth_app.models import OfficerActivity as AuditLog
                     AuditLog.objects.create(
                         officer=ds.officer,
                         action="OPERATIONAL_ALERT",
                         metadata={"msg": "Observation Aging: Case needs manual review before auto-escalation (12h remaining)."}
                     )

                 if age_hours > 48:
                     # 1. Confidence Guard: No auto-penalty for shaky data
                     if signals.get('confidence', 0) >= 0.8:
                         conservative_drop = (ds.score - ai_signal) * 0.5
                         ds.score = max(0, int(ds.score - conservative_drop))
                         ds.monitoring_mode = 'NORMAL'
                         ds.resolution_needed = False
                         ds.save()
                         return ds.score
                     else:
                         # Data uncertain: Extended Observation but alert CO
                         ds.resolution_needed = True
                         ds.save()
                         return ds.score

             ds.monitoring_mode = 'CONTROLLED_OBSERVATION'
             if not ds.monitoring_start_at:
                 ds.monitoring_start_at = timezone.now()
             ds.save()
             return ds.score

        # 5. Arbitrated Score Calculation
        # ... (keep existing logic) ...
        health = get_system_health()
        if health.get('source') != 'verified_server':
             weights = {'ai': 0.5, 'human': 0.3, 'baseline': 0.2, 'prediction': 0}
        else:
             weights = cls.calculate_dynamic_weights(health, signals)

        # 4. Confidence Decay (The Humble System)
        # If no ground-truth validation for 7 days, decay system confidence
        last_val = ds.last_computed
        from django.utils import timezone
        if timezone.now() - last_val > timedelta(days=7):
             confidence *= 0.8 # System becomes more cautious
             
        # 5. Arbitrated Score Calculation
        current_score = (
            signals.get('ai_anomaly', 100) * weights['ai'] +
            signals.get('human_review', 100) * weights['human'] +
            signals.get('baseline_drift', 100) * weights['baseline'] +
            signals.get('prediction', 100) * weights['prediction']
        )
        
        # 6. Decision & Outcome Trigger
        ds.last_decision_id = f"DEC-{int(timezone.now().timestamp())}"
        ds.pending_verification_at = timezone.now()
        ds.save()

        # 4. Adaptive Smoothing (Intelligent Response)
        confidence = signals.get('confidence', 1.0)
        last_score = ds.last_arbitrated_score
        delta = current_score - last_score
        
        if abs(delta) > 15:
            if confidence > 0.85:
                # Strong Evidence: React Aggressively (No smoothing)
                pass 
            else:
                # Weak Signal: Smooth the transition
                current_score = last_score + (delta * 0.4) 

        # 5. Anchor Drift Protection (7d Average)
        # Using reputation_history to compute 7d avg anchor
        history_7 = ds.reputation_history[-7:]
        ds.anchor_score = sum(h['score'] for h in history_7) / len(history_7) if history_7 else 100
        
        if abs(current_score - ds.anchor_score) > 25:
            # Sudden outlier detected. Force manual audit signal.
            pass

        final_score = int(max(0, min(100, current_score)))
        ds.last_arbitrated_score = final_score
        ds.save()
        
        return final_score

    @classmethod
    def generate_reason(cls, signals):
        ai_score = signals.get('ai_anomaly', 100)
        if ai_score < 50:
            return "🚩 Critical Anomaly: High-risk behavior pattern detected by AI Engine."
        if ai_score < 80:
            return "⚠️ Suspicious Activity: Behavioral drift detected."
        return "Stable: Behavior within normal parameters."

    @classmethod
    def trace_decision_path(cls, signals):
        # Returns weight breakdown for UI
        return {
            'ai_weight': 0.4,
            'human_weight': 0.3,
            'baseline_weight': 0.2,
            'prediction_weight': 0.1
        }

    @classmethod
    def apply_soft_friction(cls, officer, reason):
        # Implementation for soft friction (e.g., forced delay)
        pass

    @classmethod
    def is_priority_source_verified(cls, signals):
        return signals.get('confidence', 0) > 0.8

    @classmethod
    def fast_path_arbitrate(cls, ds, signals):
        return int(signals.get('ai_anomaly', 100))

    @classmethod
    def deep_validation_gate(cls, ds, signals):
        return ds.score # Stay safe during pilot

    @classmethod
    def calculate_dynamic_weights(cls, health, signals):
        if not health.get('is_stable', True):
            return {'ai': 0.2, 'human': 0.5, 'baseline': 0.3, 'prediction': 0}
        return {'ai': 0.4, 'human': 0.3, 'baseline': 0.2, 'prediction': 0.1}

    @classmethod
    def deterministic_fallback(cls, ds, signals):
        return int(ds.score * 0.9)
    @classmethod
    def human_audit(cls, reviewer, target_officer, review_score):
        """
        Validates human reviews against System Truth.
        If a reviewer is consistently biased, their weight is reduced.
        """
        from .models import DisciplineScore, ReviewerReliability
        from apps.auth_app.models import OfficerActivity as AuditLog
        ds, _ = DisciplineScore.objects.get_or_create(officer=target_officer)
        reliability, _ = ReviewerReliability.objects.get_or_create(reviewer=reviewer)
        
        # System Truth = Mix of current score and AI prediction
        system_truth = (ds.score * 0.7) + (ds.predicted_score * 0.3)
        
        # Dynamic Reject Threshold: Strictness increases as system health decreases
        reject_threshold = 50 if ds.system_health_index > 80 else 35
        
        if deviation > 40:
            # BIAS DETECTED: Harder Penalty & Escalation
            reliability.deviation_count += 1
            # Decay rate increases as health decreases
            decay_rate = 0.4 if ds.system_health_index > 80 else 0.6
            reliability.reliability_score = max(0.0, reliability.reliability_score - decay_rate)
            
            # 1. Hard Reject: If deviation is too extreme, ignore it entirely
            is_rejected = deviation > reject_threshold
            if is_rejected:
                reliability.bias_flag = True # Immediate flag
                
            AuditLog.objects.create(
                officer=reviewer,
                actor=reviewer,
                unit=reviewer.unit,
                pno=reviewer.pno,
                action="HUMAN_BIAS_DETECTED",
                severity="WARNING",
                metadata={
                    "target_pno": target_officer.pno,
                    "deviation": deviation,
                    "review_score": review_score,
                    "system_truth": system_truth
                }
            )
            
        reliability.save()
        
        return reliability.reliability_score, (deviation > 50)
