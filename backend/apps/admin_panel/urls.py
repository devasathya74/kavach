from django.urls import path
from .views import (
    AdminUserListView, AdminUserActionView, AdminAssignTrainingView, 
    AdminSuspiciousUsersView, AdminUserCreateView, AdminOrderCreateView, AdminAlertCreateView,
    AdminStatsView, AdminReportView, AdminAuditLogView, AppVersionCheckView, 
    AppDownloadView, AppUpdateLogView, SystemHealthView, AdminDecisionDashboardView,
    AdminLiveFeedView, SystemAnalyticsView, RemoteConfigView, ApproveAppReleaseView
)

urlpatterns = [
    path('users',           AdminUserListView.as_view()),
    path('user/create',     AdminUserCreateView.as_view()),
    path('user/action',     AdminUserActionView.as_view()),
    path('assign-training', AdminAssignTrainingView.as_view()),
    path('suspicious',      AdminSuspiciousUsersView.as_view()),
    path('orders/create',   AdminOrderCreateView.as_view()),
    path('alerts/create',   AdminAlertCreateView.as_view()),
    path('stats',           AdminStatsView.as_view()),
    path('reports/export',  AdminReportView.as_view()),
    path('audit-logs/',     AdminAuditLogView.as_view(), name='admin_audit_logs'),
    path('app-version/',    AppVersionCheckView.as_view(), name='app_version_check'),
    path('app-download/',   AppDownloadView.as_view(), name='app_download'),
    path('app-release/approve/', ApproveAppReleaseView.as_view(), name='app_release_approve'),
    path('app-update/log/', AppUpdateLogView.as_view(), name='app_update_log'),
    path('system-health/',  SystemHealthView.as_view(), name='system_health'),
    path('decision-dashboard/', AdminDecisionDashboardView.as_view(), name='decision_dashboard'),
    path('live-feed',       AdminLiveFeedView.as_view(), name='admin_live_feed'),
    path('analytics',       SystemAnalyticsView.as_view(), name='system_analytics'),
    path('config',          RemoteConfigView.as_view(), name='remote_config'),
]
