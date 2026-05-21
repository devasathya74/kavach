from .audit_models import DecisionAudit
from .models import DisciplineScore
from django.utils import timezone
from datetime import timedelta

class MirrorEngine:
    def __init__(self, audit_id=None):
        self.audit_id = audit_id

    def record_initial_snapshot(self, officer, action):
        ds = DisciplineScore.objects.filter(officer=officer).first()
        pre_score = ds.score if ds else 100.0
        
        return DecisionAudit.objects.create(
            officer=officer,
            action_type=action,
            pre_score=pre_score
        )

    def evaluate_effectiveness(self, audit):
        """
        Evaluate if the behavior improved 48 hours after the decision.
        """
        ds = DisciplineScore.objects.filter(officer=audit.officer).first()
        if not ds:
            return
            
        post_score = ds.score
        improvement = post_score - audit.pre_score
        
        # Effectiveness Score Logic
        # Scale: -100 to +100 -> 0.0 to 1.0
        # Positive improvement gives higher score
        raw_effectiveness = (improvement + 100) / 200.0
        
        status = 'NEUTRAL'
        if raw_effectiveness > 0.6: status = 'EFFECTIVE'
        elif raw_effectiveness < 0.4: status = 'INEFFECTIVE'
        
        audit.post_score = post_score
        audit.effectiveness_score = round(raw_effectiveness, 2)
        audit.status = status
        audit.evaluated_at = timezone.now()
        audit.save()
        
        return status

    @staticmethod
    def get_co_performance():
        total = DecisionAudit.objects.filter(status__ne='PENDING').count()
        if total == 0: return {}
        
        effective = DecisionAudit.objects.filter(status='EFFECTIVE').count()
        return {
            'total_decisions': total,
            'effectiveness_rate': round((effective / total) * 100, 2),
            'summary': f"{effective} out of {total} decisions were successful."
        }
