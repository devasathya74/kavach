from django.urls import path
from .views import SystemHealthView, ActiveAlertsView

urlpatterns = [
    path('monitoring/health/', SystemHealthView.as_view(), name='system-health'),
    path('monitoring/alerts/', ActiveAlertsView.as_view(), name='active-alerts'),
]
