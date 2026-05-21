from django.urls import path, include

from django.http import JsonResponse

from django.db import connection
from django.core.cache import cache

from .health_views import health_live, health_ready, health_deep, metrics_view

urlpatterns = [
    # ── Telemetry Endpoint (Root Level) ──────────────────
    path('metrics/',        metrics_view, name='metrics'),

    # ── API v1 ───────────────────────────────────────────
    path('api/v1/', include([
        # ── Telemetry Endpoint (v1 level) ────────────────
        path('metrics/',     metrics_view, name='api_v1_metrics'),

        # ── Startup/Root APIs ──────────────────────────────────
        path('',                include('apps.auth_app.urls')),
        path('',                include('apps.monitoring.urls')),
        path('',                include('apps.admin_panel.urls')),

        # ── Health Checks ──────────────────────────────────────
        path('health/live/',     health_live, name='health_live'),
        path('health/ready/',    health_ready, name='health_ready'),
        path('health/deep/',     health_deep, name='health_deep'),
        
        # ── Module Specific Prefixes (Legacy/Admin) ────────────
        path('auth/',           include('apps.auth_app.urls')),
        path('training/',       include('apps.training.urls')),
        path('orders/',         include('apps.orders.urls')),
        path('behavior/',       include('apps.behavior.urls')),
        path('admin/',          include('apps.admin_panel.urls')),
        path('monitoring/',     include('apps.monitoring.urls')),

        path('consent',         include('apps.auth_app.consent_urls')),
        path('device/',         include('apps.auth_app.device_urls')),
    ])),
    # ── API v2 (Production Architecture) ──────────────────
    path('api/v2/', include([
        path('',                include('apps.auth_app.urls_v2')),
    ])),
]
