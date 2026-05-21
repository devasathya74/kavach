from django.db import models
from django.conf import settings
import uuid


class BehaviorEvent(models.Model):
    """
    All misuse events from the Android app.
    Stored per officer — admin queries patterns.
    """
    EVENT_TYPES = [
        ('SEEK_ATTEMPT',      'Video Seek Attempt'),
        ('APP_BACKGROUND',    'App Backgrounded During Video'),
        ('APP_FOREGROUND',    'App Resumed'),
        ('QUIZ_FAST_ANSWER',  'Quiz Answer Too Fast'),
        ('QUIZ_ATTEMPT',      'Quiz Attempt'),
        ('TRAINING_COMPLETE', 'Training Completed'),
        ('DEVICE_MISMATCH',   'Device Mismatch'),
        ('HEARTBEAT_GAP',     'Heartbeat Gap Detected'),
        ('POSITION_STUCK',    'Video Position Not Advancing'),
        ('QUIZ_TOO_FAST',     'Quiz Completed Too Fast'),
    ]

    id          = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    officer     = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE,
                                    related_name='behavior_events')
    training_id = models.CharField(max_length=100, blank=True)
    event_type  = models.CharField(max_length=30, choices=EVENT_TYPES)
    timestamp_ms= models.BigIntegerField()                # from device
    received_at = models.DateTimeField(auto_now_add=True) # server time
    metadata    = models.JSONField(default=dict)

    class Meta:
        db_table = 'behavior_events'
        ordering = ['-received_at']
        indexes  = [
            models.Index(fields=['officer', 'event_type']),
            models.Index(fields=['officer', 'received_at']),
            models.Index(fields=['event_type']),
            models.Index(fields=['timestamp_ms']),
        ]


class DisciplineScore(models.Model):
    """
    Computed discipline score per officer.
    Recalculated on each behavior batch upload.
    Admin sees this — not raw events.
    """
    GRADE_CHOICES = [
        ('EXCELLENT', 'Excellent'),
        ('GOOD',      'Good'),
        ('WARNING',   'Warning'),
        ('FLAGGED',   'Flagged'),
    ]

    RISK_LEVEL_CHOICES = [
        ('NORMAL',    'Normal'),
        ('SUSPICIOUS',  'Suspicious'),
        ('HIGH_RISK',   'High Risk'),
    ]

    officer        = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE,
                                          related_name='discipline_score')
    score          = models.IntegerField(default=100)    # 0–100
    grade          = models.CharField(max_length=10, choices=GRADE_CHOICES, default='EXCELLENT')
    risk_level     = models.CharField(max_length=20, choices=RISK_LEVEL_CHOICES, default='NORMAL')
    class TrustLevel(models.TextChoices):
        TRUSTED = "TRUSTED", "Trusted (High Integrity)"
        NORMAL = "NORMAL", "Normal"
        WATCHLIST = "WATCHLIST", "Watchlist (Risky)"
        RESTRICTED = "RESTRICTED", "Restricted (High Risk)"

    trust_level = models.CharField(
        max_length=20, 
        choices=TrustLevel.choices, 
        default=TrustLevel.NORMAL
    )

    anomaly_score  = models.IntegerField(default=0)      # 0–100
    anomaly_reasons= models.JSONField(default=list)
    
    # Leader Detection Fields
    influence_score = models.FloatField(default=0.0)    # 0.0 to 1.0
    is_command_node = models.BooleanField(default=False)
    
    # Recovery & Enforcement
    last_violation_at = models.DateTimeField(null=True, blank=True)
    good_behavior_streak_hours = models.IntegerField(default=0)
    enforcement_active = models.BooleanField(default=False)
    enforcement_reason = models.TextField(blank=True)
    override_restriction = models.BooleanField(default=False)
    
    requires_review= models.BooleanField(default=False)
    performance_tag = models.CharField(max_length=50, blank=True) # ELITE, HIGH DISCIPLINE
    performance_msg = models.TextField(blank=True)
    
    # Reputation Operational Metrics
    last_activity_at = models.DateTimeField(auto_now=True)
    reputation_history = models.JSONField(default=list) # [{date, score, tier}]
    # Anticipatory & Cluster Defense
    momentum_score = models.FloatField(default=0.0)
    predicted_score = models.IntegerField(default=100)
    trajectory_status = models.CharField(max_length=20, default='STABLE') # RISING, FALLING, VOLATILE
    
    cluster_bias_flag = models.BooleanField(default=False)
    
    # Decision Stability & Audit
    last_arbitrated_score = models.IntegerField(default=100)
    decision_audit_log = models.JSONField(default=list) 

    # Adaptive Stability
    anchor_score = models.IntegerField(default=100) # 7-day average reference
    safe_mode_trigger_count = models.IntegerField(default=0)
    last_safe_mode_at = models.DateTimeField(null=True, blank=True)

    # Fairness & Decisiveness Layer
    monitoring_mode = models.CharField(max_length=20, default='NORMAL')
    monitoring_start_at = models.DateTimeField(null=True, blank=True)
    accumulated_suspicion = models.FloatField(default=0.0)
    resolution_needed = models.BooleanField(default=False)

    # Self-Evaluating Intelligence
    system_accuracy_stats = models.JSONField(default=dict) # {correct, total, last_outcome}
    pending_verification_at = models.DateTimeField(null=True, blank=True)
    last_decision_id = models.CharField(max_length=50, null=True, blank=True)

    # Audit-Proof Learning Layer
    learning_version = models.IntegerField(default=1)
    model_weights_snapshot = models.JSONField(default=dict) # current active weights
    learning_freeze_active = models.BooleanField(default=False)
    # Distrust Everything Layer
    system_health_index = models.FloatField(default=100.0)
    cumulative_drift = models.FloatField(default=0.0)
    anchor_version = models.IntegerField(default=1)
    last_rollback_at = models.DateTimeField(null=True, blank=True)
    
    # Operational Clarity Layer
    last_decision_explanation = models.JSONField(default=dict) # {reason, confidence, signals, status}
    is_experimental_mode = models.BooleanField(default=False)
    
    event_summary  = models.JSONField(default=dict)      # event_type → count
    last_computed  = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'discipline_scores'

class CharacterEntry(models.Model):
    """
    Official digital character roll entry for an officer.
    Used for the PDF reporting system and formal governance.
    """
    pno = models.CharField(max_length=20, db_index=True)
    name = models.CharField(max_length=100)
    unit = models.CharField(max_length=100)
    timestampMs = models.BigIntegerField(db_index=True)

    level = models.CharField(max_length=10)   # L1–L4
    score = models.IntegerField()

    remark = models.TextField()

    created_by = models.CharField(max_length=50)  # CO name
    created_at = models.DateTimeField(auto_now_add=True)

    # tamper-proof
    hash_signature = models.CharField(max_length=256)

    class Meta:
        db_table = 'character_entries'
        ordering = ['-created_at']

    def generate_hash(self):
        import hashlib
        data = f"{self.pno}{self.score}{self.remark}{self.created_at}"
        return hashlib.sha256(data.encode()).hexdigest()

    def save(self, *args, **kwargs):
        if not self.hash_signature:
            from django.utils import timezone
            if not self.created_at:
                self.created_at = timezone.now()
            self.hash_signature = self.generate_hash()
        super().save(*args, **kwargs)

class Violation(models.Model):
    VIOLATION_TYPES = [
        ('CRITICAL_ACK_DELAY', 'Critical Alert ACK Delay'),
        ('DEVICE_TAMPER',     'Device/Environment Tamper'),
        ('TRAINING_SKIP',     'Training Circumvention Attempt'),
    ]

    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='violations')
    v_type = models.CharField(max_length=30, choices=VIOLATION_TYPES)
    severity = models.CharField(max_length=10, default='NORMAL')
    is_validated = models.BooleanField(default=False)
    validation_note = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'violations'
        ordering = ['-created_at']

class PenaltyRecord(models.Model):
    PENALTY_TYPES = [
        ('AUTO_DELAY', 'Automatic Delay Penalty'),
        ('AUTO_ACK',   'Automatic ACK Failure Penalty'),
        ('MANUAL',     'Manual Administrative Penalty'),
    ]

    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='penalties')
    violation = models.OneToOneField(Violation, on_delete=models.SET_NULL, null=True, blank=True)
    penalty_type = models.CharField(max_length=20, choices=PENALTY_TYPES)
    score_deduction = models.IntegerField()
    reason = models.TextField()
    is_reversed = models.BooleanField(default=False)
    reversed_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'penalty_records'
        ordering = ['-created_at']

    def __str__(self):
        return f"Penalty for {self.officer.pno}: -{self.score_deduction}"

# Add this to DisciplineScore class manually if needed
# monitoring_mode = models.CharField(max_length=20, default='NORMAL')

class PilotMetric(models.Model):
    date = models.DateField(auto_now_add=True)
    total_decisions = models.IntegerField(default=0)
    total_overrides = models.IntegerField(default=0)
    total_rollbacks = models.IntegerField(default=0)
    total_defers = models.IntegerField(default=0)
    avg_confidence = models.FloatField(default=0.0)
    system_accuracy = models.FloatField(default=0.0)
    alert_count = models.IntegerField(default=0)
    avg_review_time_sec = models.IntegerField(default=0)
    blind_approvals = models.IntegerField(default=0)
    class Meta:
        db_table = 'pilot_metrics'

class RemoteConfig(models.Model):
    key = models.CharField(max_length=100, unique=True)
    value = models.JSONField()
    description = models.TextField(blank=True)
    last_updated = models.DateTimeField(auto_now=True)
    class Meta:
        db_table = 'remote_configs'
class ReviewerReliability(models.Model):
    reviewer = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='reliability')
    reliability_score = models.FloatField(default=1.0) # 0.0 to 1.0
    bias_flag = models.BooleanField(default=False)
    deviation_count = models.IntegerField(default=0)
    total_reviews = models.IntegerField(default=0)
    last_updated = models.DateTimeField(auto_now=True)
    
    class Meta:
        db_table = 'reviewer_reliability'
