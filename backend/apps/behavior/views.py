from django.conf import settings
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.exceptions import PermissionDenied
import hmac
import hashlib
from django.utils import timezone
from apps.auth_app.permissions import IsAdminRole

from .models import BehaviorEvent, DisciplineScore
from .anomaly import AnomalyEngine
from .prediction_engine import PredictionEngine
from .suggestion_engine import SuggestionEngine


def _compute_score(officer):
    events = BehaviorEvent.objects.filter(officer=officer)
    summary = {}
    for e in events:
        summary[e.event_type] = summary.get(e.event_type, 0) + 1
    
    score = 100
    score -= summary.get('QUIZ_TOO_FAST', 0) * 5
    score -= summary.get('APP_BACKGROUND', 0) * 2
    score = max(0, score)
    
    grade = 'EXCELLENT'
    if score < 50: grade = 'POOR'
    elif score < 75: grade = 'GOOD'
    
    return {'score': score, 'grade': grade, 'event_summary': summary}


class BehaviorEventsView(APIView):
    def verify_signature(self, request):
        signature = request.headers.get('X-Signature')
        if not signature:
            if not settings.DEBUG:
                raise PermissionDenied("Missing Request Signature")
            return
        data = request.body.decode('utf-8')
        generated = hmac.new(settings.SECRET_KEY.encode(), data.encode(), hashlib.sha256).hexdigest()
        if generated != signature and not settings.DEBUG:
            raise PermissionDenied("Invalid Request Signature")

    def post(self, request):
        self.verify_signature(request)
        events_data = request.data.get('events', [])
        officer = request.user
        created = []
        
        for e in events_data:
            metadata = e.get('metadata', {})
            read_time = metadata.get('read_time', 0)
            expected_time = metadata.get('expected_time', 0)
            if expected_time > 0 and read_time < (expected_time * 0.6):
                raise PermissionDenied("Invalid behavior: API Tampering Detected")

            obj = BehaviorEvent(
                officer=officer,
                training_id=e.get('training_id', ''),
                event_type=e.get('event_type', ''),
                timestamp_ms=e.get('timestamp_ms', 0),
                metadata=metadata
            )
            created.append(obj)

        BehaviorEvent.objects.bulk_create(created, ignore_conflicts=True)

        # Process Engines
        result = _compute_score(officer)
        engine = AnomalyEngine(officer)
        engine.evaluate_all_patterns()

        predictor = PredictionEngine(officer)
        pred_score, pred_status = predictor.run_prediction()

        sug_engine = SuggestionEngine(officer)
        sug_type, sug_score = sug_engine.generate_suggestion()

        # Save Score
        ds, _ = DisciplineScore.objects.update_or_create(
            officer=officer,
            defaults={
                'score': result['score'],
                'grade': result['grade'],
                'event_summary': result['event_summary']
            }
        )

        return Response({
            'status': 'success', 
            'events_stored': len(created),
            'risk_level': engine.final_risk,
            'anomaly_score': engine.final_score,
            'confidence': engine.confidence,
            'reasons': engine.reasons,
            'prediction': {
                'status': pred_status,
                'score': pred_score
            },
            'suggestion': {
                'type': sug_type,
                'score': sug_score
            }
        })

from .mirror_engine import MirrorEngine

class UserScoreView(APIView):
    def get(self, request):
        # ... (standard user score logic) ...
        
        # If Admin, include CO Mirror Summary & Top Performers
        res_data = {'status': 'success', 'data': {}}
        if request.user.role in ['ADMIN', 'SUPERUSER']:
            res_data['data']['co_mirror'] = MirrorEngine.get_co_performance()
            top_performers = DisciplineScore.objects.filter(score__gte=90).order_by('-score')[:5]
            res_data['data']['top_performers'] = [
                {'name': tp.officer.name, 'pno': tp.officer.pno, 'score': tp.score} 
                for tp in top_performers
            ]
            
        try:
            ds = request.user.discipline_score
            
            # Tier Calculation (Real-time Percentile)
            total_count = DisciplineScore.objects.count()
            if total_count > 0:
                rank_pos = DisciplineScore.objects.filter(score__gt=ds.score).count() + 1
                percentile = (rank_pos / total_count) * 100
                if percentile <= 10: tier = "TOP 10% (ELITE)"
                elif percentile <= 70: tier = "MIDDLE 60% (STABLE)"
                else: tier = "BOTTOM 30% (RISK)"
            else:
                tier = "N/A"

            from .privileges import get_user_privileges
            privileges = get_user_privileges(request.user)

            res_data['data'].update({
                'pno': request.user.pno,
                'discipline_score': ds.score,
                'trust_level':     ds.trust_level,
                'performance_tag': ds.performance_tag,
                'feedback_msg':    ds.performance_msg,
                'reputation_tier': tier,
                'privileges':      privileges,
                'history':         ds.reputation_history,
                'enforcement': {
                    'active': ds.enforcement_active,
                    'mode': 'CONTROLLED_ACCESS' if not ds.override_restriction else 'OVERRIDDEN',
                    'reason': ds.enforcement_reason,
                    'recovery_streak_hours': ds.good_behavior_streak_hours
                },
                'grade':           ds.grade,
                'risk_level':      ds.risk_level,
                'anomaly_reasons': ds.anomaly_reasons
            })
            return Response(res_data)
        except DisciplineScore.DoesNotExist:
            return Response({'status': 'success', 'data': {'pno': request.user.pno, 'discipline_score': 100}})


class CharacterEntryView(APIView):
    permission_classes = [IsAdminRole]
    def post(self, request):
        # Admin only
        if not request.user.is_staff:
            raise PermissionDenied("Only CO/Admin can enter character records.")
            
        from .models import CharacterEntry
        data = request.data
        entry = CharacterEntry.objects.create(
            pno=data.get('pno'),
            name=data.get('name'),
            unit=data.get('unit'),
            timestampMs=data.get('timestampMs'),
            level=data.get('level'),
            score=data.get('score'),
            remark=data.get('remark'),
            created_by=request.user.name
        )
        return Response({'status': 'success', 'id': entry.id})

    def get(self, request):
        from .models import CharacterEntry
        pno = request.query_params.get('pno')
        entries = CharacterEntry.objects.filter(pno=pno) if pno else CharacterEntry.objects.all()[:20]
        data = [{
            'pno': e.pno, 'name': e.name, 'level': e.level, 
            'score': e.score, 'remark': e.remark, 'created_at': e.created_at
        } for e in entries]
        return Response({'status': 'success', 'entries': data})


class DisciplineReportPDFView(APIView):
    permission_classes = [IsAdminRole]
    def get(self, request):
        # Stub for PDF generation
        return Response({
            'status': 'success', 
            'message': 'PDF Generation Engine is being initialized. Please use the web dashboard for real-time reports.'
        })


class StateDashboardView(APIView):
    permission_classes = [IsAdminRole]
    def get(self, request):
        if not request.user.is_staff:
            raise PermissionDenied()
            
        from django.db.models import Count, Avg
        from .models import DisciplineScore, BehaviorEvent
        
        stats = {
            'total_officers': DisciplineScore.objects.count(),
            'avg_discipline_score': DisciplineScore.objects.aggregate(Avg('score'))['score__avg'] or 0,
            'high_risk_count': DisciplineScore.objects.filter(risk_level='HIGH_RISK').count(),
            'recent_events_count': BehaviorEvent.objects.filter(received_at__gt=timezone.now() - timezone.timedelta(days=1)).count(),
            'trust_distribution': list(DisciplineScore.objects.values('trust_level').annotate(count=Count('trust_level')))
        }
        return Response({'status': 'success', 'stats': stats})
