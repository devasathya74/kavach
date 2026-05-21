from django.urls import path, include
from .views import (
    BehaviorEventsView,
    UserScoreView,
    CharacterEntryView,
    DisciplineReportPDFView,
    StateDashboardView
)

urlpatterns = [
    path('events/', BehaviorEventsView.as_view(), name='behavior-events'),
    path('score/', UserScoreView.as_view(), name='user-score'),
    path('character-roll/', CharacterEntryView.as_view(), name='character-roll'),
    path('reports/pdf/', DisciplineReportPDFView.as_view(), name='discipline-report-pdf'),
    path('state-dashboard/', StateDashboardView.as_view(), name='state-dashboard'),
    path('config/',          include('apps.behavior.remote_config_urls')),
]
