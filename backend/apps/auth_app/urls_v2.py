from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views_v2 import (
    UserManagementViewSet, IncidentViewSet, BroadcastViewSet, 
    FieldDataViewSet, OtaUpdateViewSet, TrainingViewSet,
    AuditTimelineViewSet, DeviceCenterViewSet, DraftChangeViewSet
)

router = DefaultRouter()
router.register(r'auth/users', UserManagementViewSet, basename='user-mgmt')
router.register(r'auth/v2/incidents', IncidentViewSet, basename='incidents-v2')
router.register(r'auth/v2/broadcasts', BroadcastViewSet, basename='broadcasts-v2')
router.register(r'auth/v2/field-data', FieldDataViewSet, basename='field-data-v2')
router.register(r'auth/v2/ota-updates', OtaUpdateViewSet, basename='ota-updates-v2')
router.register(r'auth/v2/training', TrainingViewSet, basename='training-v2')
router.register(r'auth/v2/audit-timeline', AuditTimelineViewSet, basename='audit-timeline-v2')
router.register(r'auth/v2/devices', DeviceCenterViewSet, basename='devices-v2')
router.register(r'auth/v2/pending-changes', DraftChangeViewSet, basename='pending-changes-v2')

urlpatterns = [
    path('', include(router.urls)),
]
