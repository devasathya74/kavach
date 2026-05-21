"""
KAVACH — Integrity Metrics Dashboard
=====================================

HARDENED v3 — ISSUE 3 + ISSUE 8 fixes:

ISSUE 3 FIX: Metrics endpoint is an intelligence leak.
  OLD: Any `is_staff` admin can see raw failure patterns
  NEW: Requires `is_superuser` (not just is_staff)
       Returns AGGREGATE metrics only — no raw identifiers
       No individual officer IDs, device IDs, or failure timestamps
       Failure events are bucketed (hourly counts only)

ISSUE 8 (from v2): Audit trail exists.
  This is kept — but tightened per ISSUE 3.

GET /api/v1/auth/integrity/metrics/
  Requires: is_superuser = True (not just is_staff)
  Returns: hourly breakdown + running totals + anomaly flags
           NO raw identifiers, NO individual event data

Why superuser only (not is_staff)?
  is_staff: officers who can access Django admin panel (too broad)
  is_superuser: only the core technical administrator
  In a breach scenario, an attacker with a compromised staff account
  should NOT be able to see failure spikes and detection effectiveness.
"""
import time
from django.utils import timezone
from django.core.cache import cache
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated, BasePermission

from apps.auth_app.models import Officer


class IsSuperuserOnly(BasePermission):
    """
    ISSUE 3 FIX: Metrics endpoint requires superuser, not just staff.

    is_staff = Django admin access (too broad — includes many officers)
    is_superuser = only core technical administrator

    Why this matters:
    If an attacker compromises a staff account, they should NOT see:
      - failure rate spikes (tells them how effective their bypass attempts are)
      - BASIC device ratio (tells them how many devices are in degraded state)
      - downgrade event counts (shows detection sensitivity)
    """
    message = "Integrity metrics require superuser access. Staff access is insufficient."

    def has_permission(self, request, view):
        return (
            request.user and
            request.user.is_authenticated and
            request.user.is_superuser
        )


class IntegrityMetricsView(APIView):
    """
    GET /api/v1/auth/integrity/metrics/

    Returns AGGREGATE attestation health for the last 24 hours.
    Superuser-only. Returns NO raw identifiers.

    Response:
        {
            "current_hour": { ... },          # aggregated counts only
            "last_24h": { ... },              # aggregated counts only
            "anomalies": [ ... ],             # types and rates — no identifiers
            "device_trust_summary": { ... },  # counts only — no officer names/IDs
            "key_rotation_reminder": { ... }  # ISSUE 8 addition: key rotation status
        }
    """
    permission_classes = [IsAuthenticated, IsSuperuserOnly]

    def get(self, request):
        now = timezone.now()

        # ── Current hour metrics ────────────────────────────
        current_hour_key = now.strftime('%Y%m%d_%H')
        current = _get_hour_metrics(current_hour_key)

        # ── Last 24 hours (aggregate) ─────────────────────────
        last_24h = {
            'total': 0, 'STRONG': 0, 'DEVICE': 0, 'BASIC': 0,
            'DEGRADED': 0, 'FAILED': 0, 'downgrades': 0,
            'debug_clients': 0, 'strong_fallbacks': 0
        }
        for i in range(24):
            past     = timezone.now() - timezone.timedelta(hours=i)
            hour_key = past.strftime('%Y%m%d_%H')
            hour_data = _get_hour_metrics(hour_key)
            for k, v in hour_data.items():
                last_24h[k] = last_24h.get(k, 0) + v

        total_24h = max(last_24h['total'], 1)

        # ── Device Trust Summary (AGGREGATE counts — no identifiers) ─
        trusted      = Officer.objects.filter(device_trust_score__gte=80, is_active=True).count()
        basic_only   = Officer.objects.filter(device_trust_score__lt=40, device_trust_score__gt=0, is_active=True).count()
        blocked_dev  = Officer.objects.filter(is_untrusted_device=True, is_active=True).count()
        # ISSUE 3: No individual officer/device IDs returned — only counts

        # ── Computed rates ────────────────────────────────────
        failure_rate    = (last_24h['FAILED']        / total_24h) * 100
        basic_ratio     = (last_24h['BASIC']         / total_24h) * 100
        degraded_ratio  = (last_24h['DEGRADED']      / total_24h) * 100
        debug_ratio     = (last_24h['debug_clients'] / total_24h) * 100
        fallback_ratio  = (last_24h['strong_fallbacks'] / max(last_24h['STRONG'] + last_24h['DEVICE'], 1)) * 100

        # ── Anomaly Detection ────────────────────────────────
        anomalies = []

        if failure_rate > 5:
            anomalies.append({
                'type':     'HIGH_FAILURE_RATE',
                'severity': 'HIGH' if failure_rate > 15 else 'MEDIUM',
                'rate_pct': round(failure_rate, 1),
                'detail':   f"Integrity failure rate: {failure_rate:.1f}% in 24h (threshold: 5%)"
            })

        if basic_ratio > 20:
            anomalies.append({
                'type':     'HIGH_BASIC_RATIO',
                'severity': 'MEDIUM',
                'rate_pct': round(basic_ratio, 1),
                'detail':   f"BASIC integrity: {basic_ratio:.1f}% of attestations (threshold: 20%). Possible rooted device cluster."
            })

        if last_24h['downgrades'] > 10:
            anomalies.append({
                'type':     'INTEGRITY_DOWNGRADE_SPIKE',
                'severity': 'HIGH',
                'count':    last_24h['downgrades'],
                'detail':   f"{last_24h['downgrades']} integrity downgrade events in 24h. Investigate for session hijacking."
            })

        if degraded_ratio > 30:
            anomalies.append({
                'type':     'HIGH_DEGRADED_RATIO',
                'severity': 'MEDIUM',
                'rate_pct': round(degraded_ratio, 1),
                'detail':   f"DEGRADED ratio: {degraded_ratio:.1f}%. Possible Google Play API connectivity issues or network disruption."
            })

        if debug_ratio > 5:
            anomalies.append({
                'type':     'HIGH_DEBUG_CLIENT_RATIO',
                'severity': 'LOW',
                'rate_pct': round(debug_ratio, 1),
                'detail':   f"Debug clients: {debug_ratio:.1f}% of attestations. Expected ~0% in production. (Telemetry only — not a security gate)"
            })

        if fallback_ratio > 15:
            anomalies.append({
                'type':     'HIGH_STRONG_FALLBACK_RATIO',
                'severity': 'MEDIUM',
                'rate_pct': round(fallback_ratio, 1),
                'detail':   f"DEVICE-acting-as-STRONG fallbacks: {fallback_ratio:.1f}%. Investigate device fleet for STRONG attestation capability."
            })

        # ── Key Rotation Reminder (ISSUE 8 addition) ─────────
        # Backend doesn't manage key rotation — this is a reminder display only.
        # Actual rotation is done by the security team via Google Cloud Console.
        key_rotation_info = {
            'reminder': (
                'KAVACH integrity ecosystem depends on:\n'
                '  1. Google Play Integrity API key (PLAY_INTEGRITY_DECODING_KEY)\n'
                '  2. Django SECRET_KEY (used in session_tag generation)\n'
                '  3. Device HMAC secrets (per-device, stored in SessionDataStore)\n'
                '\n'
                'Rotation policy: quarterly (every 90 days).\n'
                'Use overlapping validity windows during rotation to prevent officer lockout.\n'
                'See: docs/security/key-rotation-runbook.md'
            ),
            'note': 'This system does not auto-rotate keys. Manual rotation required.'
        }

        return Response({
            'status': 'success',
            'data': {
                'current_hour': {
                    **current,
                    'failure_rate_pct': round(
                        (current['FAILED'] / max(current['total'], 1)) * 100, 1
                    )
                },
                'last_24h': {
                    **last_24h,
                    'failure_rate_pct':      round(failure_rate, 1),
                    'basic_ratio_pct':        round(basic_ratio, 1),
                    'degraded_ratio_pct':     round(degraded_ratio, 1),
                    'debug_client_ratio_pct': round(debug_ratio, 1),
                    'strong_fallback_ratio':  round(fallback_ratio, 1),
                },
                'device_trust_summary': {
                    # ISSUE 3: Counts only — no names, IDs, or per-device breakdown
                    'trusted_count':       trusted,
                    'basic_only_count':    basic_only,
                    'blocked_count':       blocked_dev,
                    'note': 'Aggregate counts only. Individual device data is not exposed via this endpoint.'
                },
                'anomalies':          anomalies,
                'key_rotation_info':  key_rotation_info,
                'generated_at':       now.isoformat(),
                'access_note':        'Superuser access only. Aggregate data only. No raw identifiers.'
            }
        })


def _get_hour_metrics(hour_key: str) -> dict:
    """Fetch all integrity metrics for a given hour key."""
    levels = ['STRONG', 'DEVICE', 'BASIC', 'DEGRADED', 'FAILED']
    data = {
        'total':           cache.get(f"integrity_total_{hour_key}", 0),
        'downgrades':      cache.get(f"integrity_downgrades_{hour_key}", 0),
        'debug_clients':   cache.get(f"integrity_debug_clients_{hour_key}", 0),
        'strong_fallbacks': cache.get(f"integrity_strong_fallback_{hour_key}", 0),
    }
    for level in levels:
        data[level] = cache.get(f"integrity_{level.lower()}_{hour_key}", 0)
    return data
