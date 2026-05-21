from django.db import models
from django.conf import settings
import uuid


class Training(models.Model):
    STATUS_CHOICES = [
        ('DRAFT',     'Draft'),
        ('PUBLISHED', 'Published'),
        ('ARCHIVED',  'Archived'),
    ]
    id          = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    title       = models.CharField(max_length=200)
    description = models.TextField()
    video_path  = models.CharField(max_length=500, default='')   # Storage path
    duration    = models.IntegerField()              # seconds — used for validation
    is_mandatory= models.BooleanField(default=True)
    status      = models.CharField(max_length=10, choices=STATUS_CHOICES, default='DRAFT')
    created_by  = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
                                    null=True, related_name='created_trainings')
    created_at  = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'trainings'


class TrainingAssignment(models.Model):
    """Admin assigns specific trainings to specific officers with deadlines."""
    training   = models.ForeignKey(Training, on_delete=models.CASCADE)
    officer    = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    deadline   = models.DateTimeField(null=True, blank=True)
    assigned_by= models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
                                   null=True, related_name='assignments_made')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table        = 'training_assignments'
        unique_together = ('training', 'officer')


class TrainingSession(models.Model):
    """
    Tracks each officer's training attempt.
    Server uses this as the source of truth — not client.
    """
    STATUS_CHOICES = [
        ('PENDING',     'Pending'),
        ('IN_PROGRESS', 'In Progress'),
        ('COMPLETED',   'Completed'),
        ('FAILED',      'Failed'),
        ('SUSPICIOUS',  'Suspicious'),  # completed too fast
    ]
    id               = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    officer          = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    training         = models.ForeignKey(Training, on_delete=models.CASCADE)
    session_id       = models.CharField(max_length=100, unique=True)  # from Android
    status           = models.CharField(max_length=12, choices=STATUS_CHOICES, default='PENDING')

    started_at       = models.DateTimeField(auto_now_add=True)
    completed_at     = models.DateTimeField(null=True, blank=True)

    # Heartbeat tracking
    last_heartbeat   = models.DateTimeField(null=True, blank=True)
    heartbeat_count  = models.IntegerField(default=0)
    last_position_ms = models.BigIntegerField(default=0)

    # Quiz validation
    quiz_started_at  = models.DateTimeField(null=True, blank=True)
    quiz_submitted_at= models.DateTimeField(null=True, blank=True)
    quiz_score       = models.IntegerField(null=True)
    quiz_passed      = models.BooleanField(null=True)
    quiz_attempts    = models.IntegerField(default=0)

    # Server-side suspicious flag
    is_suspicious    = models.BooleanField(default=False)
    suspicious_reason= models.CharField(max_length=200, blank=True)

    class Meta:
        db_table        = 'training_sessions'
        unique_together = ('officer', 'training')


class Heartbeat(models.Model):
    """Individual heartbeat records — for gap analysis."""
    session     = models.ForeignKey(TrainingSession, on_delete=models.CASCADE,
                                    related_name='heartbeats')
    position_ms = models.BigIntegerField()
    received_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'heartbeats'
        ordering = ['received_at']


class QuizQuestion(models.Model):
    training      = models.ForeignKey(Training, on_delete=models.CASCADE,
                                      related_name='questions')
    question      = models.TextField()
    option_a      = models.CharField(max_length=500)
    option_b      = models.CharField(max_length=500)
    option_c      = models.CharField(max_length=500)
    option_d      = models.CharField(max_length=500)
    correct_option= models.CharField(max_length=1)  # 'A', 'B', 'C', 'D'

    class Meta:
        db_table = 'quiz_questions'
