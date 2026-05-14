# Kavach Project Structure

## Frontend (Android App)
```text
app
|-- archive
|   +-- ui_legacy
|       |-- admin
|       |   +-- AdminDashboardViewModel.kt
|       |-- dashboard
|       |-- field
|       |   +-- FieldDataScreen.kt
|       |-- live
|       |   |-- LiveBroadcastScreen.kt
|       |   +-- LiveViewModel.kt
|       +-- ota
|           +-- OtaUpdateScreen.kt
|-- core
|   |-- attention
|   |   +-- AttentionBudget.kt
|   |-- auth
|   |   |-- AuthManager.kt
|   |   +-- SystemRole.kt
|   |-- authority
|   |   +-- CommandAuthorityLanguage.kt
|   |-- backend
|   |   +-- BackendContract.kt
|   |-- clock
|   |   +-- TrustedClock.kt
|   |-- crypto
|   |   +-- CommandSignatureEngine.kt
|   |-- degraded
|   |   +-- DegradedModeController.kt
|   |-- events
|   |   |-- EventBus.kt
|   |   +-- SystemEvent.kt
|   |-- export
|   |   +-- IncidentPackageExporter.kt
|   |-- forensics
|   |   +-- ForensicSnapshotSystem.kt
|   |-- graph
|   |   +-- CommandGraphEngine.kt
|   |-- latency
|   |   +-- CommandLatencyTracker.kt
|   |-- metrics
|   |   +-- TrustMetrics.kt
|   |-- perf
|   |   +-- PerformanceMonitor.kt
|   |-- policy
|   |   +-- PolicyEngine.kt
|   |-- protocol
|   |   +-- ProtocolVersion.kt
|   |-- reconciliation
|   |   +-- StateReconciliationEngine.kt
|   |-- redteam
|   |   +-- RedTeamSimulator.kt
|   |-- resource
|   |   +-- ResourceAdaptationEngine.kt
|   |-- security
|   |   |-- AppLockManager.kt
|   |   |-- AppLockState.kt
|   |   |-- AuthorizationEngine.kt
|   |   |-- BiometricAuthManager.kt
|   |   |-- CommandModeController.kt
|   |   |-- PinManager.kt
|   |   |-- SecureScreenController.kt
|   |   |-- ThreatEffects.kt
|   |   |-- ThreatLevel.kt
|   |   +-- ThreatStateManager.kt
|   |-- sequencing
|   |   +-- EventSequencer.kt
|   |-- session
|   |   +-- SessionRecoveryManager.kt
|   |-- simulation
|   |   +-- OperationalSimulator.kt
|   |-- sound
|   |   +-- SoundManager.kt
|   |-- telemetry
|   |   |-- NetworkTelemetry.kt
|   |   |-- TelemetryManager.kt
|   |   +-- TelemetrySimulator.kt
|   |-- testing
|   |   +-- FailureTestHarness.kt
|   +-- timeline
|       +-- CommandTimelineEngine.kt
|-- data
|   |-- local
|   |   |-- dao
|   |   |   |-- Daos.kt
|   |   |   |-- IncidentDao.kt
|   |   |   +-- OfficerDao.kt
|   |   |-- db
|   |   |   +-- KavachDatabase.kt
|   |   |-- entity
|   |   |   |-- Entities.kt
|   |   |   |-- IncidentDraftEntities.kt
|   |   |   |-- OfficerCacheEntities.kt
|   |   |   +-- OperationalEntities.kt
|   |   |-- SessionDataStore.kt
|   |   +-- StoragePressureManager.kt
|   |-- remote
|   |   |   |-- AuthRefreshApiService.kt
|   |   |   |-- KavachApiService.kt
|   |   |   |-- KavachApiV2.kt
|   |   |   +-- TokenAuthenticator.kt
|   |   |-- dto
|   |   |   |-- auth
|   |   |   |   |-- AuthDtos.kt
|   |   |   |   +-- UserDto.kt
|   |   |   |-- broadcast
|   |   |   |   +-- BroadcastDto.kt
|   |   |   |-- common
|   |   |   |   +-- ApiResponse.kt
|   |   |   |-- incident
|   |   |   |   +-- IncidentDto.kt
|   |   |   |-- orders
|   |   |   |   +-- OrdersDtos.kt
|   |   |   |-- personnel
|   |   |   |   |-- AdminOfficerDto.kt
|   |   |   |   +-- OfficerDto.kt
|   |   |   |-- system
|   |   |   |   |-- DraftChangeDto.kt
|   |   |   |   |-- OtaUpdateDto.kt
|   |   |   |   +-- SystemDtos.kt
|   |   |   |-- training
|   |   |   |   +-- TrainingDtos.kt
|   |   |   +-- v2
|   |   |       |-- V2AuthRequests.kt
|   |   |       +-- V2Dtos.kt
|   |   |-- websocket
|   |   |   +-- WebSocketManager.kt
|   |   +-- worker
|   |       |-- IncidentSyncWorker.kt
|   |       +-- PersonnelSyncWorker.kt
|   |-- repository
|   |   |-- AdminRepository.kt
|   |   |-- AuthRepository.kt
|   |   |-- local
|   |   |-- mapper
|   |   |-- MediaRepository.kt
|   |   |-- MissionRepository.kt
|   |   |-- OrderRepository.kt
|   |   |-- remote
|   |   |-- sync
|   |   |-- TrainingRepository.kt
|   |   +-- UserManagementRepository.kt
|   +-- sync
|       |-- AdaptiveThrottling.kt
|       |-- LocalDomainEventBus.kt
|       +-- SyncCoordinator.kt
|-- di
|   +-- AppModule.kt
|-- domain
|   +-- model
|       |-- Order.kt
|       |-- Training.kt
|       +-- User.kt
|-- KavachApp.kt
|-- KavachConfig.kt
|-- MainActivity.kt
|-- receiver
|   +-- BootCompletedReceiver.kt
|-- security
|-- service
|   +-- KavachMessagingService.kt
|-- ui
|   |-- components
|   |   |-- ConnectivityBanner.kt
|   |   |-- FilterChipGroup.kt
|   |   |-- KavachBadges.kt
|   |   |-- TacticalSecurityComponents.kt
|   |   +-- WatermarkOverlay.kt
|   |-- navigation
|   |   |-- graphs
|   |   |-- KavachNavHost.kt
|   |   |-- NavHostViewModel.kt
|   |   |-- navigators
|   |   |-- RoleRouter.kt
|   |   +-- Screen.kt
|   |-- overlay
|   |   |-- OverlayViewModel.kt
|   |   +-- TacticalOverlaySystem.kt
|   |-- screens
|   |   |-- alerts
|   |   |   +-- CriticalAlertScreen.kt
|   |   |-- broadcast
|   |   |   |-- BroadcastInboxViewModel.kt
|   |   |   +-- CreateBroadcastViewModel.kt
|   |   |-- common
|   |   |-- dashboard
|   |   |   |-- CommandCenterScreen.kt
|   |   |   |-- DashboardAuthority.kt
|   |   |   |-- DashboardViewModel.kt
|   |   |   +-- UnifiedDashboardScreen.kt
|   |   |-- device
|   |   |   |-- DeviceStatusScreen.kt
|   |   |   +-- DeviceStatusViewModel.kt
|   |   |-- incident
|   |   |   |-- IncidentFeedScreen.kt
|   |   |   +-- IncidentFeedViewModel.kt
|   |   |   +-- LockViewModel.kt
|   |   |-- login
|   |   |   |-- LoginScreen.kt
|   |   |   +-- LoginViewModel.kt
|   |   |-- orders
|   |   |   |-- OrderScreens.kt
|   |   |   +-- OrderViewModels.kt
|   |   |-- permissions
|   |   |   |-- PermissionGateScreen.kt
|   |   |   +-- PermissionViewModel.kt
|   |   |-- pilot
|   |   |   |-- approvals
|   |   |   |   |-- ApprovalListScreen.kt
|   |   |   |   +-- ApprovalListViewModel.kt
|   |   |   |-- audit
|   |   |   |   |-- AuditTimelineScreen.kt
|   |   |   |   +-- AuditTimelineViewModel.kt
|   |   |   |-- broadcast
|   |   |   |   |-- BroadcastCenterScreen.kt
|   |   |   |   +-- BroadcastCenterViewModel.kt
|   |   |   |-- ConnectivityViewModel.kt
|   |   |   |-- data
|   |   |   |   |-- FieldDataScreen.kt
|   |   |   |   +-- FieldDataViewModel.kt
|   |   |   |-- devices
|   |   |   |   |-- DeviceCenterScreen.kt
|   |   |   |   +-- DeviceCenterViewModel.kt
|   |   |   |-- DiagnosticViewModel.kt
|   |   |   |-- incident
|   |   |   |   |-- IncidentCenterScreen.kt
|   |   |   |   +-- IncidentCenterViewModel.kt
|   |   |   |-- live
|   |   |   |   |-- LiveBroadcastScreen.kt
|   |   |   |   +-- LiveViewModel.kt
|   |   |   |-- ota
|   |   |   |   |-- OtaUpdateScreen.kt
|   |   |   |   +-- OtaUpdateViewModel.kt
|   |   |   |-- personnel
|   |   |   |   |-- OfficerDetailScreen.kt
|   |   |   |   |-- OfficerDetailViewModel.kt
|   |   |   |   |-- PersonnelListItemUiModel.kt
|   |   |   |   |-- PersonnelListScreen.kt
|   |   |   |   |-- PersonnelListViewModel.kt
|   |   |   |   |-- UserRegistrationScreen.kt
|   |   |   |   +-- UserRegistrationViewModel.kt
|   |   |   +-- training
|   |   |       |-- TrainingScreen.kt
|   |   |       +-- TrainingViewModel.kt
|   |   |-- profile
|   |   |   +-- ProfileScreen.kt
|   |   |-- security
|   |   |   |-- SecureAccessScreen.kt
|   |   |   |-- SecureAccessViewModel.kt
|   |   |   |-- SecurityEnrollmentScreen.kt
|   |   |   +-- SecurityEnrollmentViewModel.kt
|   |   |-- splash
|   |   |   +-- SplashScreen.kt
|   |   +-- training
|   |       +-- TrainingViewModels.kt
|   |-- theme
|   |   |-- Color.kt
|   |   |-- Theme.kt
|   |   +-- Type.kt
|   |-- tokens
|   |   +-- TacticalTokens.kt
|   +-- transitions
|       +-- TacticalTransitions.kt
|-- util
|   |-- AudioRecorder.kt
|   |-- AutoUpdateManager.kt
|   |-- NetworkMonitor.kt
|   |-- SecureStorage.kt
|   +-- SecurityUtils.kt
+-- utils
    |-- ApiResult.kt
    |-- BehaviorScoreCalculator.kt
    |-- BehaviorTracker.kt
    |-- DeviceIdUtil.kt
    |-- EmulatorDetectionUtil.kt
    |-- HeartbeatManager.kt
    |-- KavachSyncWorker.kt
    |-- OperationalStrings.kt
    |-- RootDetectionUtil.kt
    |-- SessionTimeoutManager.kt
    +-- SyncAckWorker.kt
```

## Backend (Django)
```text
backend
|-- __structure__.py
|-- apps
|   |-- admin_panel
|   |   |-- consumers.py
|   |   |-- migrations
|   |   |   |-- __init__.py
|   |   |   |-- 0001_initial.py
|   |   |   |-- 0002_apprelease_expires_at_apprelease_rollout_percentage.py
|   |   |   |-- 0003_apprelease_is_critical_override_and_more.py
|   |   |   |-- 0004_apprelease_approval_reason_and_more.py
|   |   |   +-- 0005_apprelease_blast_radius_apprelease_incident_id_and_more.py
|   |   |-- models.py
|   |   |-- urls.py
|   |   +-- views.py
|   |-- auth_app
|   |   |-- consent_urls.py
|   |   |-- device_urls.py
|   |   |-- health_views.py
|   |   |-- integrity_decorators.py
|   |   |-- integrity_metrics.py
|   |   |-- integrity_views.py
|   |   |-- management
|   |   |   +-- commands
|   |   |       |-- operational_cleanup.py
|   |   |       |-- reset_operational_data.py
|   |   |       |-- seed_governance.py
|   |   |       +-- seed_pilot_data.py
|   |   |-- middleware.py
|   |   |-- migrations
|   |   |   |-- __init__.py
|   |   |   |-- 0001_initial.py
|   |   |   |-- 0002_officer_email.py
|   |   |   |-- 0003_officer_fcm_token_officer_role_alter_officer_pno_and_more.py
|   |   |   |-- 0004_officer_blind_approval_count_and_more.py
|   |   |   |-- 0005_officer_is_pilot_alter_officer_role.py
|   |   |   |-- 0006_remove_officer_is_pilot.py
|   |   |   |-- 0007_officer_challenge_count_officer_daily_otp_count_and_more.py
|   |   |   |-- 0008_remove_auditlog_device_id_auditlog_device_id_hash_and_more.py
|   |   |   |-- 0009_pilotoverriderequest.py
|   |   |   |-- 0010_pilotoverriderequest_approval_correlation_id_and_more.py
|   |   |   |-- 0011_officer_app_channel.py
|   |   |   |-- 0012_companymaster_rankmaster_unitmaster_and_more.py
|   |   |   |-- 0013_adversarialsignal_constitutionalarticle_and_more.py
|   |   |   |-- 0014_recoveryaction_and_more.py
|   |   |   |-- 0015_unitmaster_type.py
|   |   |   +-- 0016_officer_must_change_password_and_more.py
|   |   |-- models
|   |   |   |-- __init__.py
|   |   |   |-- auth.py
|   |   |   |-- broadcast.py
|   |   |   |-- governance.py
|   |   |   |-- incident.py
|   |   |   |-- infrastructure.py
|   |   |   |-- realtime.py
|   |   |   +-- training.py
|   |   |-- orchestration
|   |   |   |-- event_bus.py
|   |   |   |-- event_contract.py
|   |   |   |-- realtime_gateway.py
|   |   |   |-- recovery.py
|   |   |   |-- resilience.py
|   |   |   |-- scheduler.py
|   |   |   +-- verification.py
|   |   |-- override_engine.py
|   |   |-- permissions.py
|   |   |-- security
|   |   |   |-- __init__.py
|   |   |   |-- ranks.py
|   |   |   +-- roles.py
|   |   |-- serializers.py
|   |   |-- urls.py
|   |   |-- urls_v2.py
|   |   |-- utils.py
|   |   |-- views.py
|   |   +-- views_v2.py
|   |-- behavior
|   |   |-- anomaly.py
|   |   |-- audit_models.py
|   |   |-- bias_engine.py
|   |   |-- chain_break_models.py
|   |   |-- decision_core.py
|   |   |-- drills.py
|   |   |-- fingerprint.py
|   |   |-- graph_engine.py
|   |   |-- graph_models.py
|   |   |-- input_trust.py
|   |   |-- learning_engine.py
|   |   |-- management
|   |   |   +-- commands
|   |   |       +-- run_graph_engine.py
|   |   |-- middleware.py
|   |   |-- migrations
|   |   |   |-- __init__.py
|   |   |   |-- 0001_initial.py
|   |   |   |-- 0002_characterentry_disciplinescore_anomaly_reasons_and_more.py
|   |   |   |-- 0003_behavioralcluster_pilotmetric_remoteconfig_and_more.py
|   |   |   +-- 0004_reviewerreliability.py
|   |   |-- mirror_engine.py
|   |   |-- ml_engine.py
|   |   |-- models.py
|   |   |-- neo4j_client.py
|   |   |-- permissions.py
|   |   |-- pilot_controller.py
|   |   |-- prediction_engine.py
|   |   |-- prediction_models.py
|   |   |-- privileges.py
|   |   |-- remote_config_urls.py
|   |   |-- remote_config_views.py
|   |   |-- review_engine.py
|   |   |-- review_models.py
|   |   |-- suggestion_engine.py
|   |   |-- suggestion_models.py
|   |   |-- urls.py
|   |   +-- views.py
|   |-- monitoring
|   |   |-- __init__.py
|   |   |-- adaptive.py
|   |   |-- apps.py
|   |   |-- management
|   |   |   |-- __init__.py
|   |   |   +-- commands
|   |   |       |-- __init__.py
|   |   |       +-- run_alert_engine.py
|   |   |-- migrations
|   |   |   |-- __init__.py
|   |   |   +-- 0001_initial.py
|   |   |-- models.py
|   |   |-- urls.py
|   |   |-- utils.py
|   |   +-- views.py
|   |-- orders
|   |   |-- __init__.py
|   |   |-- migrations
|   |   |   |-- __init__.py
|   |   |   |-- 0001_initial.py
|   |   |   +-- 0002_alert_alertack.py
|   |   |-- models.py
|   |   |-- urls.py
|   |   +-- views.py
|   +-- training
|       |-- migrations
|       |   |-- __init__.py
|       |   |-- 0001_initial.py
|       |   +-- 0002_remove_training_s3_key_training_video_path.py
|       |-- models.py
|       |-- urls.py
|       +-- views.py
|-- archive
|   +-- orchestration_experimental
|       |-- analysis.py
|       |-- archival.py
|       |-- awareness.py
|       |-- backpressure.py
|       |-- circuit_breaker.py
|       |-- cognition.py
|       |-- commands.py
|       |-- consolidation.py
|       |-- continuity.py
|       |-- convergence.py
|       |-- degradation.py
|       |-- enforcement.py
|       |-- entropy_governance.py
|       |-- forensics.py
|       |-- humility.py
|       |-- incident_policy.py
|       |-- interpretation.py
|       |-- legitimacy.py
|       |-- meta_governance.py
|       |-- narration.py
|       |-- philosophy.py
|       |-- policies.py
|       |-- policy_engine.py
|       |-- read_fencing.py
|       |-- realism.py
|       |-- reconciliation.py
|       |-- refusal.py
|       |-- registry.py
|       |-- shared_context.py
|       |-- slo_engine.py
|       |-- snapshot_hierarchy.py
|       |-- sovereignty.py
|       |-- stability.py
|       |-- stability_phase.py
|       |-- survivability.py
|       |-- transfer.py
|       +-- trust_graph.py
|-- k8s
|-- kavach_backend
|   |-- __init__.py
|   |-- api_middleware.py
|   |-- asgi.py
|   |-- celery.py
|   |-- exception_handler.py
|   |-- health_views.py
|   |-- middleware.py
|   |-- session_security.py
|   |-- settings.py
|   |-- supabase_client.py
|   +-- urls.py
|-- manage.py
|-- pilot_operations_config.py
|-- scripts
|   |-- governance_cleanup.py
|   +-- outbox_dispatcher.py
|-- seeds
|   |-- bootstrap_admin.py
|   |-- create_test_users.py
|   |-- reset_auth_db.py
|   |-- seed_data.py
|   |-- seed_pilot_deployment.py
|   |-- seed_pilot_users.py
|   |-- seed_v3.py
|   +-- setup_pilot_admin.py
|-- setup_db.py
|-- templates
+-- tools
    |-- pilot_telemetry_report.py
    |-- simulate_attack_phase_1.py
    |-- simulate_human_bias_attack.py
    |-- simulate_multi_attack.py
    +-- verify_release.py
```
