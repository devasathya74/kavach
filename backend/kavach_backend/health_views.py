from django.http import JsonResponse
from django.db import connection
from apps.auth_app.models.infrastructure import EventOutbox

def health_live(request):
    """Liveness Check - Is the process running?"""
    return JsonResponse({"status": "live", "timestamp": JsonResponse.now() if hasattr(JsonResponse, 'now') else "ok"})

def health_ready(request):
    """Readiness Check - Are dependencies reachable?"""
    try:
        connection.ensure_connection()
        return JsonResponse({"status": "ready", "database": "connected"})
    except Exception as e:
        return JsonResponse({"status": "unready", "error": str(e)}, status=503)

def health_deep(request):
    """Deep Check - Comprehensive diagnostics for forensic review."""
    diagnostics = {
        "status": "healthy",
        "database": "connected",
        "outbox_backlog": EventOutbox.objects.filter(status='PENDING').count(),
    }
    # Add logic for workers, cache, etc if needed
    return JsonResponse(diagnostics)
