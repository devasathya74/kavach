from django.urls import path, include
from .views import (
    LoginView, VerifyOtpView, RefreshTokenView, CheckUserRoleView,
    ProfileView, ChangePasswordView, RegisterFcmTokenView,
    NotificationAckView, LogoutView,
    AppVersionView, AppUpdateLogView,       # ← OTA endpoints (AllowAny)
)
from .integrity_views import IntegrityNonceView, IntegrityVerifyView
from .integrity_metrics import IntegrityMetricsView
from .views_v2 import (
    UserManagementViewSet, DraftChangeViewSet, DeviceCenterViewSet, 
    AuditTimelineViewSet, IncidentViewSet, BroadcastViewSet,
    FieldDataViewSet, OtaUpdateViewSet, TrainingViewSet
)
from rest_framework.routers import DefaultRouter

router = DefaultRouter()
router.register('users', UserManagementViewSet, basename='users')
router.register('pending-changes', DraftChangeViewSet, basename='pending-changes')
router.register('devices', DeviceCenterViewSet, basename='devices')
router.register('audit-timeline', AuditTimelineViewSet, basename='audit-timeline')
router.register('incidents', IncidentViewSet, basename='incidents')
router.register('broadcasts', BroadcastViewSet, basename='broadcasts')
router.register('field-data', FieldDataViewSet, basename='field-data')
router.register('ota-updates', OtaUpdateViewSet, basename='ota-updates')
router.register('training', TrainingViewSet, basename='training')

urlpatterns = [
    path('login/',            LoginView.as_view(),           name='login'),
    path('verify-otp/',       VerifyOtpView.as_view(),       name='verify-otp'),
    path('refresh/',          RefreshTokenView.as_view(),    name='token-refresh'),
    path('check-user/',       CheckUserRoleView.as_view(),   name='check-user-role'),
    path('profile/',          ProfileView.as_view(),         name='profile'),
    path('change-password/',  ChangePasswordView.as_view(),  name='change-password'),
    path('register-fcm/',     RegisterFcmTokenView.as_view(), name='register-fcm'),
    path('notification-ack/', NotificationAckView.as_view(), name='notification-ack'),
    path('logout/',           LogoutView.as_view(),          name='logout'),

    # ── OTA / App Version (AllowAny — called before login) ─
    path('app-version/',      AppVersionView.as_view(),      name='app-version'),
    path('app-update/log/',   AppUpdateLogView.as_view(),    name='app-update-log'),

    # ── Play Integrity API ──────────────────────────────────
    path('integrity/nonce/',   IntegrityNonceView.as_view(),   name='integrity-nonce'),
    path('integrity/verify/',  IntegrityVerifyView.as_view(),  name='integrity-verify'),
    path('integrity/metrics/', IntegrityMetricsView.as_view(), name='integrity-metrics'),
    
    # ── v2 Operational Platform ──────────────────────────────
    path('v2/',               include(router.urls)),
]
