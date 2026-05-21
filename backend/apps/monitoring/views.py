try:
    import psutil
except ImportError:
    psutil = None
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAdminUser, AllowAny
from .models import AlertRule, SystemAlert
from .utils import get_avg_delivery_delay

class SystemHealthView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        if psutil is None:
            return Response({
                "status": "HEALTHY (Limited)",
                "metrics": {
                    "cpu_percent": "N/A",
                    "mem_percent": "N/A",
                    "disk_percent": "N/A",
                    "avg_delivery_delay_sec": get_avg_delivery_delay(),
                }
            })
        return Response({
            "status": "HEALTHY",
            "metrics": {
                "cpu_percent": psutil.cpu_percent(),
                "mem_percent": psutil.virtual_memory().percent,
                "disk_percent": psutil.disk_usage('/').percent,
                "avg_delivery_delay_sec": get_avg_delivery_delay(),
            }
        })

class ActiveAlertsView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        alerts = SystemAlert.objects.filter(is_resolved=False)
        return Response([{
            "id": alert.id,
            "rule": alert.rule.name,
            "message": alert.message,
            "severity": alert.severity,
            "value": alert.current_value,
            "created_at": alert.created_at
        } for alert in alerts])

    def post(self, request, pk=None):
        # Mark as resolved
        alert_id = request.data.get('alert_id')
        if alert_id:
            SystemAlert.objects.filter(id=alert_id).update(is_resolved=True, resolved_at=timezone.now())
            return Response({"status": "resolved"})
        return Response({"error": "alert_id required"}, status=400)
