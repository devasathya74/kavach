from datetime import timedelta
from django.utils import timezone
from django.conf import settings
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
import boto3
from botocore.signers import CloudFrontSigner
import rsa, datetime

from .models import Training, TrainingSession, Heartbeat, QuizQuestion
from apps.behavior.models import BehaviorEvent


from kavach_backend.supabase_client import get_signed_url

def _generate_signed_url(path: str, expiry_seconds: int = 3600) -> str:
    """
    Generate a Supabase signed URL for secure video delivery.
    """
    return get_signed_url("training-videos", path, expiry_seconds)


from django.utils.decorators import method_decorator
from django.views.decorators.cache import cache_page

class TrainingListView(APIView):
    @method_decorator(cache_page(60 * 5))
    def get(self, request):
        officer   = request.user
        trainings = Training.objects.filter(status='PUBLISHED')
        data = []
        for t in trainings:
            try:
                session = TrainingSession.objects.get(officer=officer, training=t)
                sess_status = session.status
            except TrainingSession.DoesNotExist:
                sess_status = 'PENDING'

            data.append({
                'id':          str(t.id),
                'title':       t.title,
                'description': t.description,
                'duration':    t.duration,
                'is_mandatory':t.is_mandatory,
                'status':      sess_status,
                # No video URL here — issued only on start
            })
        return Response({'status': 'success', 'data': data})


class StartTrainingView(APIView):
    """
    POST /api/training/start
    { training_id, session_id }

    Returns a signed CDN URL valid for (duration + 30 min).
    URL cannot be shared — tied to this request timing.
    """
    def post(self, request):
        training_id = request.data.get('training_id')
        session_id  = request.data.get('session_id', '')

        try:
            training = Training.objects.get(id=training_id, status='PUBLISHED')
        except Training.DoesNotExist:
            return Response({'status': 'error', 'message': 'Training नहीं मिली'}, status=404)

        session, created = TrainingSession.objects.get_or_create(
            officer  = request.user,
            training = training,
            defaults = {'session_id': session_id, 'status': 'IN_PROGRESS'}
        )
        if not created and session.status == 'COMPLETED':
            return Response({'status': 'error', 'message': 'Training पहले ही पूरी हो चुकी है'},
                            status=status.HTTP_400_BAD_REQUEST)

        session.status     = 'IN_PROGRESS'
        session.session_id = session_id
        session.save()

from apps.auth_app.views import _audit

        # Signed URL valid for video duration + 30 minute buffer
        expiry_seconds = training.duration + 1800
        signed_url     = _generate_signed_url(training.video_path, expiry_seconds)
        
        _audit(request.user, 'SIGNED_URL_TRAINING', request, training_id=str(training.id), video_path=training.video_path)

        return Response({
            'status':     'success',
            'video_url':  signed_url,
            'expires_in': expiry_seconds,
            'session_id': session_id
        })


class HeartbeatView(APIView):
    """
    POST /api/training/heartbeat
    { training_id, position_ms, session_id }

    Server validates:
    ① Session exists and is IN_PROGRESS
    ② Gap from last heartbeat < HEARTBEAT_GAP_LIMIT_SECONDS
    ③ Position is advancing (not stuck)
    """
    def post(self, request):
        training_id = request.data.get('training_id')
        position_ms = request.data.get('position_ms', 0)
        session_id  = request.data.get('session_id', '')

        try:
            session = TrainingSession.objects.get(
                officer    = request.user,
                training_id= training_id,
                session_id = session_id,
                status     = 'IN_PROGRESS'
            )
        except TrainingSession.DoesNotExist:
            return Response({'status': 'error', 'message': 'Invalid session'}, status=400)

        now = timezone.now()

        # ── Gap detection ──────────────────────────────────
        if session.last_heartbeat:
            gap_seconds = (now - session.last_heartbeat).total_seconds()
            if gap_seconds > settings.HEARTBEAT_GAP_LIMIT_SECONDS * 2:
                # Large gap → log suspicious event
                BehaviorEvent.objects.create(
                    officer      = request.user,
                    training_id  = training_id,
                    event_type   = 'HEARTBEAT_GAP',
                    metadata     = {'gap_seconds': gap_seconds, 'position_ms': position_ms}
                )

        # ── Position stuck detection ───────────────────────
        if position_ms <= session.last_position_ms + 500 and session.heartbeat_count > 0:
            BehaviorEvent.objects.create(
                officer     = request.user,
                training_id = training_id,
                event_type  = 'POSITION_STUCK',
                metadata    = {'position_ms': position_ms}
            )

        # Update session
        session.last_heartbeat   = now
        session.heartbeat_count += 1
        session.last_position_ms = position_ms
        session.save(update_fields=['last_heartbeat', 'heartbeat_count', 'last_position_ms'])

        Heartbeat.objects.create(session=session, position_ms=position_ms)

        return Response({'status': 'success'})


class SubmitQuizView(APIView):
    """
    POST /api/quiz/submit/v2
    { training_id, answers, session_id, time_taken_ms, answer_timings }

    Server-side decision engine:
    ① Validates quiz timing (not too fast)
    ② Scores answers
    ③ Checks heartbeat coverage (>= 60% of video watched)
    ④ Marks session COMPLETED or SUSPICIOUS
    """
    def post(self, request):
        training_id  = request.data.get('training_id')
        answers      = request.data.get('answers', {})
        session_id   = request.data.get('session_id', '')
        time_taken_ms= request.data.get('time_taken_ms', 0)

        try:
            session  = TrainingSession.objects.get(
                officer    = request.user,
                training_id= training_id,
                session_id = session_id
            )
            training = session.training
        except TrainingSession.DoesNotExist:
            return Response({'status': 'error', 'message': 'Session नहीं मिली'}, status=400)

        # ── Attempt limit check ────────────────────────────
        if session.quiz_attempts >= 3:
            return Response({'status': 'error', 'message': 'अधिकतम प्रयास समाप्त'},
                            status=status.HTTP_403_FORBIDDEN)

        session.quiz_attempts    += 1
        session.quiz_submitted_at = timezone.now()

        # ── Server-side timing validation ──────────────────
        # Quiz should take at least 60% of video duration
        min_quiz_time_ms = training.duration * 1000 * settings.QUIZ_MIN_TIME_RATIO
        suspicious       = False
        suspicious_reason= ''

        if time_taken_ms < min_quiz_time_ms:
            suspicious        = True
            suspicious_reason = f'quiz_too_fast: {time_taken_ms}ms < {min_quiz_time_ms}ms'
            BehaviorEvent.objects.create(
                officer     = request.user,
                training_id = training_id,
                event_type  = 'QUIZ_TOO_FAST',
                metadata    = {'time_taken_ms': time_taken_ms, 'min_required_ms': min_quiz_time_ms}
            )

        # ── Heartbeat coverage check ───────────────────────
        heartbeat_coverage = (session.heartbeat_count * settings.HEARTBEAT_GAP_LIMIT_SECONDS * 1000)
        if heartbeat_coverage < training.duration * 1000 * settings.VIDEO_COMPLETION_THRESHOLD:
            return Response({"error": "invalid_watch", "message": "Training video was not watched completely."}, status=400)

        # ── Score answers ──────────────────────────────────
        questions  = QuizQuestion.objects.filter(training=training)
        total      = questions.count()
        correct    = 0

        for q in questions:
            given  = answers.get(str(q.id), '').upper()
            if given == q.correct_option.upper():
                correct += 1

        score      = int((correct / total) * 100) if total > 0 else 0
        passed     = score >= settings.QUIZ_PASS_PERCENTAGE if hasattr(settings, 'QUIZ_PASS_PERCENTAGE') else score >= 70

        # ── Update session ────────────────────────────────
        session.quiz_score   = score
        session.quiz_passed  = passed
        session.is_suspicious= suspicious
        if suspicious:
            session.suspicious_reason = suspicious_reason
            session.status = 'SUSPICIOUS'
        elif passed:
            session.status       = 'COMPLETED'
            session.completed_at = timezone.now()
        else:
            session.status = 'FAILED'

        session.save()

        return Response({
            'status': 'success',
            'data': {
                'score':       score,
                'total':       total,
                'passed':      passed,
                'is_suspicious': suspicious
            }
        })
