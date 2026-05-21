try:
    import psutil
except ImportError:
    psutil = None
from django.utils import timezone
from datetime import timedelta
from .models import AlertRule, SystemAlert, SystemState
from apps.auth_app.models import NotificationLog
from apps.behavior.models import DisciplineScore, PenaltyRecord, Violation
from django.db.models import Avg

def get_system_health():
    """
    Returns a unified health snapshot for Decision Core.
    """
    state, _ = SystemState.objects.get_or_create(id=1)
    return {
        'source': 'verified_server',
        'adaptive_factor': state.current_adaptive_factor,
        'network_delay': state.network_delay_sec,
        'failure_rate': state.failure_rate,
        'is_stable': state.failure_rate < 0.1
    }

def get_avg_delivery_delay():
    logs = NotificationLog.objects.filter(delivered=True, acked_at__isnull=False).order_by('-created_at')[:100]
    if not logs.exists():
        return 0
    
    delays = []
    for log in logs:
        delay = (log.acked_at - log.created_at).total_seconds()
        delays.append(delay)
    
    return sum(delays) / len(delays) if delays else 0

def evaluate_rules():
    rules = AlertRule.objects.filter(is_active=True)
    for rule in rules:
        value = get_metric_value(rule.metric)
        triggered = False
        if rule.condition == ">" and value > rule.threshold: triggered = True
        
        if triggered:
            existing = SystemAlert.objects.filter(rule=rule, is_resolved=False).first()
            if not existing:
                trigger_alert(rule, value)

def trigger_alert(rule, value):
    alert = SystemAlert.objects.create(
        rule=rule,
        message=f"CRITICAL: {rule.name} is {value}",
        current_value=value,
        severity=rule.severity
    )
    # PHASE 1: Create Potential Violations (No penalty yet)
    if rule.name == "DELIVERY_DELAY" and value > 60:
        identify_potential_violations()

def identify_potential_violations():
    # Find users who haven't ACKed critical alerts for > 5 mins
    five_mins_ago = timezone.now() - timedelta(minutes=5)
    late_logs = NotificationLog.objects.filter(
        priority='CRITICAL',
        delivered=True,
        acked_at__isnull=True,
        created_at__lt=five_mins_ago
    )

    for log in late_logs:
        # 1. Create Violation Record
        v, created = Violation.objects.get_or_create(
            officer=log.officer,
            v_type='CRITICAL_ACK_DELAY',
            is_validated=False,
            created_at__gt=timezone.now() - timedelta(minutes=10) # Prevent duplicates
        )
        if created:
            # 2. RUN VALIDATION ENGINE
            process_violation(v)

def process_violation(v):
    # Rule 1: Cool-down check (Only 1 penalty per hour for same type)
    last_penalty = PenaltyRecord.objects.filter(
        officer=v.officer, 
        penalty_type='AUTO_ACK',
        created_at__gt=timezone.now() - timedelta(hours=1)
    ).exists()
    
    if last_penalty:
        v.validation_note = "Skipped: Cool-down active."
        v.save()
        return

    # Rule 2: Network Safety (Placeholder for real check)
    # If many users are failing, it's likely a system issue, not user.
    global_fail_rate = Violation.objects.filter(
        v_type=v.v_type, 
        created_at__gt=timezone.now() - timedelta(minutes=5)
    ).count()
    
    if global_fail_rate > 5:
        v.validation_note = "Skipped: High global failure rate (Network/Server Issue)."
        v.save()
        return

    # Rule 3: Validated!
    v.is_validated = True
    v.validation_note = "Validated: User ignored critical alert."
    v.save()

    # Rule 4: Scale-based Penalty
    violations_24h = Violation.objects.filter(
        officer=v.officer, 
        is_validated=True,
        created_at__gt=timezone.now() - timedelta(hours=24)
    ).count()

    deduction = 5 # Initial
    if violations_24h >= 3:
        deduction = 15 # Severe

    # APPLY PENALTY
    PenaltyRecord.objects.create(
        officer=v.officer,
        violation=v,
        penalty_type='AUTO_ACK',
        score_deduction=deduction,
        reason=v.validation_note
    )
    
    # Update Discipline Score
    score_obj, _ = DisciplineScore.objects.get_or_create(officer=v.officer)
    score_obj.score = max(0, score_obj.score - deduction)
    score_obj.save()

def get_metric_value(metric):
    if psutil is None:
        return 0
    if metric == "cpu_percent": return psutil.cpu_percent()
    if metric == "mem_percent": return psutil.virtual_memory().percent
    if metric == "delivery_avg_sec": return get_avg_delivery_delay()
    return 0
