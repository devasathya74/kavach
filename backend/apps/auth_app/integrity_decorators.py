"""
KAVACH — Integrity Enforcement Decorators
=========================================

HARDENED v3 — Round 2 issue fixes:

ISSUE 2 (Admin STRONG = operationally risky):
  require_strong_integrity now has a fallback path:
    STRONG → allow
    DEVICE + recent strong history → allow with escalation audit
    DEVICE (no history) → 403 with clear message

ISSUE 6 (Decorator fatigue — future devs forget):
  SENSITIVE_ROUTE_PATTERNS registry added.
  Middleware will auto-enforce DEVICE-level for any route matching these patterns.
  Developers don't need to remember to add decorators to /admin/, /export/ etc.

Trust Windows (level-based):
    STRONG   → 30 min
    DEVICE   → 10 min
    BASIC    →  2 min (read-only only)
    DEGRADED →  5 min
    FAILED   →  0 (always blocked)

Usage:
    from apps.auth_app.integrity_decorators import (
        require_device_integrity,
        require_strong_integrity,
        require_read_write_integrity,
        SENSITIVE_ROUTE_PATTERNS,
    )

    # Normal ops — STRONG or DEVICE required
    @require_device_integrity
    def order_acknowledge(request): ...

    # Privileged ops — STRONG preferred, DEVICE with history as fallback
    @require_strong_integrity
    def admin_bulk_action(request): ...
"""
import time
import functools
from django.core.cache import cache
from django.http import JsonResponse
from django.conf import settings


# ── Trust Windows (level-based, not flat) ─────────────────────────────────────
_TRUST_WINDOWS_MS = {
    'STRONG':   30 * 60 * 1000,   # 30 minutes
    'DEVICE':   10 * 60 * 1000,   # 10 minutes
    'BASIC':     2 * 60 * 1000,   # 2 minutes (read-only only)
    'DEGRADED':  5 * 60 * 1000,   # 5 minutes grace
    'FAILED':    0,                # Never trusted
}

# ── ISSUE 6 FIX: Sensitive Route Registry ─────────────────────────────────────
#
# Future developers: if you add a sensitive endpoint, ADD IT HERE.
# The middleware reads this list and auto-enforces require_device_integrity.
# You do NOT need to add a decorator manually for routes matching these patterns.
#
# Use DEVICE-level patterns here (middleware auto-gate).
# Use @require_strong_integrity decorator manually for STRONG-only endpoints.
#
SENSITIVE_ROUTE_PATTERNS = [
    '/admin/',           # Django admin panel
    '/export/',          # Any data export endpoint
    '/bulk/',            # Any bulk operation endpoint
    '/integrity/',       # Integrity management itself
    '/change-password/', # Account modification
    '/logout/',          # Session termination (prevent forced logout attacks)
    '/register-fcm/',    # FCM token registration (device binding)
]


def _get_integrity_context(request):
    """Extract and validate integrity headers from request."""
    level       = request.headers.get('X-Integrity-Level', '')
    attested_at = request.headers.get('X-Attested-At', '0')
    try:
        attested_at_ms = int(attested_at)
    except (ValueError, TypeError):
        attested_at_ms = 0
    return level, attested_at_ms


def _is_attestation_valid(level: str, attested_at_ms: int) -> bool:
    """Check if attestation is within the level-appropriate trust window."""
    if not level or attested_at_ms == 0:
        return False
    window_ms = _TRUST_WINDOWS_MS.get(level, 0)
    if window_ms == 0:
        return False
    age_ms = int(time.time() * 1000) - attested_at_ms
    return age_ms <= window_ms


def _get_officer_id(request) -> str:
    """Safely extract officer ID for cache lookups."""
    user = getattr(request, 'user', None)
    if user and hasattr(user, 'id'):
        return str(user.id)
    return ''


def require_device_integrity(view_func):
    """
    Decorator for views requiring STRONG or DEVICE integrity.

    Use for: normal operational views (orders, training, profile updates).

    Blocks:
      - BASIC  (no live reads — use locally cached data)
      - FAILED (compromised device)
      - Expired attestations

    DEGRADED passes (5-min grace) — user sees "limited connectivity" UI.
    """
    @functools.wraps(view_func)
    def wrapped(self_or_request, *args, **kwargs):
        # Support both class-based (self, request) and function-based (request) views
        if hasattr(self_or_request, 'dispatch'):
            request = args[0] if args else self_or_request
        else:
            request = self_or_request

        # Admin/staff bypass in DEBUG mode (for local testing)
        if settings.DEBUG and getattr(request, 'user', None) and request.user.is_staff:
            return view_func(self_or_request, *args, **kwargs)

        level, attested_at_ms = _get_integrity_context(request)

        if level == 'FAILED':
            return JsonResponse({
                'error': 'Device integrity check failed. Access denied.',
                'code':  'KAVACH_INTEGRITY_FAILED'
            }, status=403)

        if level == 'BASIC':
            return JsonResponse({
                'error': 'Device integrity insufficient for live data. Use cached data only.',
                'code':  'KAVACH_INTEGRITY_BASIC_NO_LIVE_READS',
                'required': 'DEVICE',
                'current':  'BASIC'
            }, status=403)

        if not _is_attestation_valid(level, attested_at_ms):
            return JsonResponse({
                'error': 'Device attestation expired. Re-verify to continue.',
                'code':  'KAVACH_INTEGRITY_EXPIRED',
                'required_level': 'DEVICE'
            }, status=401)

        return view_func(self_or_request, *args, **kwargs)
    return wrapped


def require_strong_integrity(view_func):
    """
    Decorator for views preferring STRONG integrity.

    ISSUE 2 FIX: Not STRONG-only anymore — this was operationally risky.
    Old Samsung, custom enterprise ROMs, and weak Play Services legitimately
    cannot achieve STRONG. Blanket rejection would lock out real officers.

    New Policy:
      STRONG → allow (hardware attestation confirmed)
      DEVICE + recent STRONG history → allow with escalation audit log
      DEVICE (no recent STRONG history) → 403 (prompt re-attest on better network)
      BASIC / FAILED → 403

    "Recent STRONG history" = officer had STRONG attestation within last 4 hours.
    This is tracked via cache key set during attestation.

    Use for: admin actions, bulk operations, config changes, account modifications.
    """
    @functools.wraps(view_func)
    def wrapped(self_or_request, *args, **kwargs):
        if hasattr(self_or_request, 'dispatch'):
            request = args[0] if args else self_or_request
        else:
            request = self_or_request

        level, attested_at_ms = _get_integrity_context(request)

        # STRONG: full pass
        if level == 'STRONG':
            if not _is_attestation_valid(level, attested_at_ms):
                return JsonResponse({
                    'error': 'Strong attestation expired. Re-verify device to perform this action.',
                    'code':  'KAVACH_INTEGRITY_EXPIRED',
                    'required_level': 'STRONG'
                }, status=401)
            return view_func(self_or_request, *args, **kwargs)

        # DEVICE: check recent STRONG history as fallback (Risk 2 + Risk 3: HMAC-bound key)
        if level == 'DEVICE':
            if not _is_attestation_valid(level, attested_at_ms):
                return JsonResponse({
                    'error': 'Device attestation expired.',
                    'code':  'KAVACH_INTEGRITY_EXPIRED',
                    'required_level': 'DEVICE'
                }, status=401)

            officer_id = _get_officer_id(request)
            # Risk 3 FIX: Derive same HMAC fingerprint as integrity_views.py.
            # Old: sha256(device_id + jwt_tail[-8:]) — attacker has the JWT, can compute this.
            # New: HMAC(SECRET_KEY[:32], device_id) — server secret never leaves server.
            #      Attacker cannot compute fingerprint without SECRET_KEY.
            #      Same device_id on same server always matches. Rotates with key rotation.
            import hmac as _hmac
            from django.conf import settings as _settings
            device_id = request.headers.get('X-Device-Id', '')
            device_binding_key = _hmac.new(
                _settings.SECRET_KEY[:32].encode(),
                device_id.encode(),
                __import__('hashlib').sha256
            ).hexdigest()[:16]

            recent_strong = cache.get(f"last_strong_integrity_{officer_id}_{device_binding_key}")
            if recent_strong:
                _log_strong_fallback_to_device(officer_id, request)
                return view_func(self_or_request, *args, **kwargs)
            else:
                return JsonResponse({
                    'error': 'This operation prefers hardware-level attestation. '
                             'Your device currently has DEVICE-level integrity. '
                             'Try again when connected to a stable network for STRONG attestation.',
                    'code':      'KAVACH_INTEGRITY_PREFER_STRONG',
                    'required':  'STRONG',
                    'current':   'DEVICE',
                    'fallback':  'DEVICE_WITH_HISTORY',
                    'has_history': False
                }, status=403)



        # BASIC / FAILED / anything else → block
        return JsonResponse({
            'error': 'This operation requires strong device attestation.',
            'code':  'KAVACH_INTEGRITY_INSUFFICIENT',
            'required': 'STRONG',
            'current':  level or 'NONE'
        }, status=403)
    return wrapped


def _log_strong_fallback_to_device(officer_id: str, request):
    """
    Record DEVICE-acting-as-STRONG escalation events.
    These are tracked as separate metric counters for anomaly detection.
    Not a security failure — but should be monitored for spikes.
    """
    from django.utils import timezone
    hour_key = timezone.now().strftime('%Y%m%d_%H')
    fallback_key = f"integrity_strong_fallback_{hour_key}"
    try:
        cache.incr(fallback_key)
    except ValueError:
        cache.set(fallback_key, 1, timeout=86400)


def require_read_write_integrity(view_func):
    """
    Decorator for views where BASIC can read but not write.

    Use for: dashboard views where BASIC users can see cached data but not submit.

    ISSUE 5 alignment: BASIC = cached local data only.
    GET requests pass (cached data is safe to return).
    POST/PUT/PATCH/DELETE blocked for BASIC.
    """
    @functools.wraps(view_func)
    def wrapped(self_or_request, *args, **kwargs):
        if hasattr(self_or_request, 'dispatch'):
            request = args[0] if args else self_or_request
        else:
            request = self_or_request

        level, attested_at_ms = _get_integrity_context(request)

        if level == 'FAILED':
            return JsonResponse({
                'error': 'Device integrity check failed.',
                'code':  'KAVACH_INTEGRITY_FAILED'
            }, status=403)

        if not _is_attestation_valid(level, attested_at_ms):
            return JsonResponse({
                'error': 'Device attestation expired.',
                'code':  'KAVACH_INTEGRITY_EXPIRED'
            }, status=401)

        # BASIC can only GET
        if level == 'BASIC' and request.method not in ('GET', 'HEAD', 'OPTIONS'):
            return JsonResponse({
                'error': 'Write operations are not permitted on this device integrity level.',
                'code':  'KAVACH_INTEGRITY_READ_ONLY',
                'current': 'BASIC'
            }, status=403)

        return view_func(self_or_request, *args, **kwargs)
    return wrapped
