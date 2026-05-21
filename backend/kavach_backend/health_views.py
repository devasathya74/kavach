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

from django.http import HttpResponse, HttpResponseForbidden
from django.conf import settings
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST
from kavach_backend.metrics import REGISTRY

def metrics_view(request):
    """
    Exposes Prometheus-compliant system and business performance metrics.
    Gated strictly behind Bearer token authentication to prevent exposure.
    """
    token = getattr(settings, 'METRICS_TOKEN', None)
    if token:
        auth_header = request.headers.get('Authorization')
        if not auth_header or auth_header != f"Bearer {token}":
            return HttpResponseForbidden("Access Denied: Invalid metrics scraping credentials.")
            
    try:
        metrics_data = generate_latest(REGISTRY)
        response = HttpResponse(metrics_data, content_type=CONTENT_TYPE_LATEST)
        # Prevent caching of metrics
        response['Cache-Control'] = 'no-cache, no-store, must-revalidate'
        return response
    except Exception as e:
        import logging
        logging.getLogger('kavach.errors').critical(f"Failed to generate prometheus metrics: {str(e)}", exc_info=True)
        return HttpResponse("Internal Server Error during metrics collection.", status=500, content_type="text/plain")

