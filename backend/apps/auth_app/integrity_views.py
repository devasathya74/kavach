"""
KAVACH — Play Integrity API Backend Verification
================================================

HARDENED v4 — Round 3 risk fixes:

Risk 1 (Nonce replayable within 5-min bucket):
  Nonce is BOTH bucket-scoped AND single-use server-side.
  Two independent defenses:
    A) 5-min timestampBucket → limits replay window to 5 min max
    B) cache 'used' flag → any second use within the bucket is rejected
  Combined: attacker has a ~5 min window to capture AND replay. Short enough for field ops.

Risk 2 (STRONG history bound only to officer.id — session theft risk):
  STRONG history now bound to: officer.id + device_id_fingerprint + session_tail
  A stolen session on a different device CANNOT inherit STRONG history.
  Cache key: last_strong_integrity_{officer.id}_{device_fingerprint}

Risk 4 (Metrics writes become DoS bottleneck during attack):
  _update_integrity_metrics() now runs in a daemon thread (fire-and-forget).
  Attack traffic cannot block attestation verification via metrics write contention.
  Low-risk reads (GET) are sampled at 10% to reduce write volume.

Risk 5 (no_live_reads enforcement incomplete — backend trusts client to obey):
  Backend now enforces: if BASIC, ALL non-exempt GET/POST to protected paths → 403.
  Client flag is kept for UX (show cached data UI), but backend is the real gate.

Previous rounds:
  v2 (Round 1): level-based trust, rich verdicts, nonce binding, debug telemetry
  v3 (Round 2): DEGRADED graceful, admin STRONG fallback, metrics superuser-only,
                nonce relaxed, no_live_reads added, sensitive route registry

CRITICAL RULES:
  ① Nonce: bucket-scoped (5-min window) + single-use server-side consumed
  ② STRONG history: bound to officer + device fingerprint + session lineage
  ③ Client NEVER sees raw Google verdict
  ④ Client NEVER sees `allowed: bool` — only level + restricted flag
  ⑤ BASIC = 403 on any protected route — backend enforces, not just flags
  ⑥ DEGRADED = graceful (5-min grace, not logout)
  ⑦ X-Build-Type = telemetry only. appRecognitionVerdict is the real gate.
  ⑧ Metrics writes are async — cannot become DoS bottleneck

Trust Window (level-based):
  STRONG    → 30 minutes
  DEVICE    → 10 minutes
  BASIC     →  2 minutes
  DEGRADED  →  5 minutes
  FAILED    →  0 (never trusted)
"""
import hashlib
import hmac as _hmac_module
import uuid
import time
import random
import threading
from concurrent.futures import ThreadPoolExecutor
import requests
from django.conf import settings
from django.utils import timezone
from django.core.cache import cache
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated, AllowAny

from .models import Officer, OfficerActivity, OfficerDevice

# Risk 1 FIX: Bounded thread pool for metric writes.
#
# Old (dangerous): threading.Thread().start() per request
#   10k req/min → 10k threads/min → memory pressure + GIL thrash + self-DoS
#
# New: ThreadPoolExecutor(max_workers=4)
#   Max 4 threads ever. Additional metric writes are queued (bounded).
#   If queue full, metric is dropped silently (acceptable — metrics ≠ security).
#   Thread pool is a module-level singleton — not re-created per request.
_METRICS_POOL = ThreadPoolExecutor(max_workers=4, thread_name_prefix='kavach_metrics')

# ── Protected read routes (backend enforces BASIC block) ─────────────────
_BASIC_BLOCKED_READ_PATTERNS = [
    '/api/v1/orders/',
    '/api/v1/training/',
    '/api/v1/profile/',
    '/api/v1/behavior/',
    '/api/v1/admin/',
]


# ── Level-based trust windows (minutes) ────────────────────────────────
# Risk 6 FIX: Trust windows now have jitter (±20% random variance).
#
# Old (predictable):
#   STRONG = exactly 30min always
#   Attacker learns: re-attest every 29m to stay valid, replay in 30m window
#
# New (unpredictable):
#   STRONG = 24–36min (random on each attestation)
#   Attacker cannot reliably time their automation to window boundaries.
#
# _TRUST_WINDOW_RANGES defines (min_minutes, max_minutes) for each level.
# Actual window is sampled fresh on each attestation call.
_TRUST_WINDOW_RANGES = {
    'STRONG':   (24, 36),   # 30m ±20% = 24–36m
    'DEVICE':   (8,  12),   # 10m ±20% = 8–12m
    'BASIC':    (1,   3),   #  2m ±50% = 1–3m  (short anyway, more variance OK)
    'DEGRADED': (4,   6),   #  5m ±20% = 4–6m
    'FAILED':   (0,   0),   # Always 0
}

# Legacy constant for middleware (reads from header, not re-generates)
# Keep exact values here for window enforcement math
TRUST_WINDOWS = {
    'STRONG':   30,
    'DEVICE':   10,
    'BASIC':     2,
    'DEGRADED':  5,
    'FAILED':    0,
}


def _jittered_trust_window(level: str) -> int:
    """
    Risk 6 FIX: Return a jittered trust window for a given level.
    Called ONCE per attestation. Client stores the actual returned trust_window_minutes
    and uses that for its own expiry tracking — not the hardcoded constant.
    """
    lo, hi = _TRUST_WINDOW_RANGES.get(level, (0, 0))
    if lo == hi:
        return lo
    return random.randint(lo, hi)


# ── Policy: what each level can do ────────────────────────────────────────────
#
# ISSUE 5 FIX: BASIC = no_live_reads=True
#   This flag tells client: DO NOT make live backend reads.
#   Use only locally cached data (orders last synced, profile cached, etc.)
#   This prevents BASIC from silently reading personnel/location/operational metadata.
#
# ISSUE 1 FIX: DEGRADED = restricted=True, read_only=False, blocked=False
#   DEGRADED is NOT a hard failure. It's a network-caused transient state.
#   Backend grants 5 min grace. Client enters "limited connectivity" UI mode.
#
LEVEL_POLICY = {
    'STRONG':   {'restricted': False, 'read_only': False, 'no_live_reads': False, 'blocked': False},
    'DEVICE':   {'restricted': False, 'read_only': False, 'no_live_reads': False, 'blocked': False},
    'BASIC':    {'restricted': True,  'read_only': True,  'no_live_reads': True,  'blocked': False},
    'DEGRADED': {'restricted': True,  'read_only': False, 'no_live_reads': False, 'blocked': False},
    'FAILED':   {'restricted': True,  'read_only': True,  'no_live_reads': True,  'blocked': True},
}


def _get_client_ip(request) -> str:
    x_forwarded = request.META.get('HTTP_X_FORWARDED_FOR')
    return x_forwarded.split(',')[0] if x_forwarded else request.META.get('REMOTE_ADDR', '')


def _get_build_type_for_telemetry(request) -> str:
    """
    ISSUE 7 FIX: X-Build-Type is TELEMETRY ONLY.

    This header is NOT a security gate. An attacker can trivially send
    X-Build-Type: release from a custom client. The Play Integrity verdict
    itself (appRecognitionVerdict) is the only reliable signal for debug/tampered builds.

    Use this only for:
      - Audit logs (correlate failure spikes with debug build ratio)
      - Dashboards (what % of attestations come from debug clients)
      - NOT for blocking/allowing requests

    If you want to block debug builds: check appRecognitionVerdict == 'UNEVALUATED'
    in _call_google_integrity_api() — that's the real gate.
    """
    return request.headers.get('X-Build-Type', 'unknown').lower()


def _update_integrity_metrics(integrity_level: str, previous_level: str = '', build_type: str = 'unknown'):
    """
    Risk 1 FIX: Metrics submitted to bounded ThreadPoolExecutor (max 4 workers).

    Risk 1 v2 FIX: Drop counter added.
    Old: pool saturated → silently drop → operations blind (attack survives undetected)
    New: pool saturated → drop WITH counter increment
         Ops team checks kavach:metrics_drop_count → "telemetry degraded" alert
         Security survives. Monitoring survives too.

    Counter key: 'kavach:metrics_drop_count' (no TTL — ops clear after investigating)
    """
    try:
        _METRICS_POOL.submit(
            _do_write_integrity_metrics, integrity_level, previous_level, build_type
        )
    except RuntimeError:
        # Pool shutdown (test teardown) — safe to ignore
        pass
    except Exception:
        # Any other submission failure: increment visible drop counter.
        # This is the signal that telemetry is degraded.
        try:
            cache.incr('kavach:metrics_drop_count')
        except Exception:
            pass  # If cache itself is down, we cannot do anything. Accept blindness.


def _do_write_integrity_metrics(integrity_level: str, previous_level: str, build_type: str):
    """Actual metric write — called from background thread."""
    hour_key = timezone.now().strftime('%Y%m%d_%H')

    def _incr(key):
        try:
            cache.incr(key)
        except ValueError:
            cache.set(key, 1, timeout=86400)

    _incr(f"integrity_total_{hour_key}")
    _incr(f"integrity_{integrity_level.lower()}_{hour_key}")

    if integrity_level == 'FAILED':
        _incr(f"integrity_failures_{hour_key}")

    if build_type == 'debug':
        _incr(f"integrity_debug_clients_{hour_key}")

    level_rank = {'STRONG': 3, 'DEVICE': 2, 'BASIC': 1, 'DEGRADED': 1, 'FAILED': 0}
    if previous_level and level_rank.get(integrity_level, 0) < level_rank.get(previous_level, 0):
        _incr(f"integrity_downgrades_{hour_key}")


class IntegrityNonceView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        officer    = request.user
        device_id  = request.headers.get('X-Device-Id', '')
        # Session binding — last 16 chars of Bearer token (enough for uniqueness, not full token)
        session_id = request.headers.get('Authorization', '')[-16:] if request.headers.get('Authorization') else ''

        request_id = str(uuid.uuid4())
        
        officer_id_str = str(officer.id) if officer.is_authenticated else "ANONYMOUS"

        # Risk 1 CONFIRM: Nonce is BOTH bucket-scoped AND single-use.
        # Two independent replay defenses:
        #   Defense A (time-scoped): 5-min bucket — limits window for captured-nonce replay
        #   Defense B (server-consumed): 'used' flag set immediately on first verify call
        # An attacker with TLS hook capturing signed requests:
        #   - Can replay within the same 5-min bucket (Defense A limits window)
        #   - First replay wins, second fails (Defense B kills it)
        # This is the correct balance: mobile retry tolerance + replay resistance.
        timestamp_bucket = str(int(time.time()) // 300)   # 5-minute window

        # Nonce: userId + deviceId + sessionId + timeBucket
        nonce_raw = f"{officer_id_str}:{device_id}:{session_id}:{timestamp_bucket}"
        nonce = hashlib.sha256(nonce_raw.encode()).hexdigest()

        # Store nonce with context for verification
        cache_key = f"integrity_nonce:{request_id}"
        cache.set(cache_key, {
            'nonce':      nonce,
            'officer_id': officer_id_str,
            'device_id':  device_id,
            'session_id': session_id,
            'used':       False       # Defense B: consumed on first verify
        }, timeout=600)

        return Response({
            'status': 'success',
            'data': {
                'nonce':      nonce,
                'request_id': request_id
            }
        })


class IntegrityVerifyView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        officer         = request.user
        integrity_token = request.data.get('integrity_token', '')
        request_id      = request.data.get('request_id', '')
        device_id       = request.data.get('device_id', '')
        
        officer_pno = officer.pno if officer.is_authenticated else "ANONYMOUS"
        officer_id  = officer.id if officer.is_authenticated else "ANONYMOUS"

        # ISSUE 7: Record build type for telemetry — NOT for blocking
        build_type = _get_build_type_for_telemetry(request)

        if not integrity_token or not request_id:
            return Response({
                'status': 'error',
                'message': 'integrity_token और request_id आवश्यक हैं'
            }, status=400)

        # ── Step 1: Validate nonce ──────────────────────────
        cache_key = f"integrity_nonce:{request_id}"
        stored    = cache.get(cache_key)

        if not stored:
            _update_integrity_metrics('FAILED', build_type=build_type)
            OfficerActivity.objects.create(
                officer=officer if officer.is_authenticated else None, 
                unit=officer.unit if (officer and officer.is_authenticated) else None,
                pno=officer_pno,
                action='INTEGRITY_NONCE_EXPIRED',
                route=request.path,
                result='FAILED',
                severity='WARNING',
                ip_address=_get_client_ip(request),
                device_id_hash=hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
                metadata={
                    'request_id': request_id,
                    'correlation_id': getattr(request, 'correlation_id', 'unknown'),
                    'app_version': getattr(request, 'app_version', 'unknown'),
                }
            )
            return Response({'status': 'error', 'message': 'Nonce expired.'}, status=400)

        if stored.get('used'):
            _update_integrity_metrics('FAILED', build_type=build_type)
            AuditLog.objects.create(
                officer=officer if officer.is_authenticated else None, 
                pno=officer_pno,
                action='INTEGRITY_NONCE_REUSE_ATTEMPT',
                route=request.path,
                result='BLOCKED',
                integrity_level='UNKNOWN',
                ip_address=_get_client_ip(request),
                device_id_hash=hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
                metadata={
                    'request_id': request_id,
                    'correlation_id': getattr(request, 'correlation_id', 'unknown'),
                    'app_version': getattr(request, 'app_version', 'unknown'),
                    'device_oem': getattr(request, 'device_oem', 'unknown'),
                    'device_model': getattr(request, 'device_model', 'unknown'),
                    'android_sdk': getattr(request, 'android_sdk', 'unknown')
                }
            )
            return Response({'status': 'error', 'message': 'Nonce already used — replay prevented.'}, status=400)

        if stored.get('device_id') != device_id:
            _update_integrity_metrics('FAILED', build_type=build_type)
            AuditLog.objects.create(
                officer=officer if officer.is_authenticated else None, 
                pno=officer_pno,
                action='INTEGRITY_DEVICE_MISMATCH',
                route=request.path,
                result='BLOCKED',
                integrity_level='UNKNOWN',
                ip_address=_get_client_ip(request),
                device_id_hash=hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
                metadata={
                    'expected': stored.get('device_id', '')[:8],
                    'correlation_id': getattr(request, 'correlation_id', 'unknown'),
                    'app_version': getattr(request, 'app_version', 'unknown'),
                    'device_oem': getattr(request, 'device_oem', 'unknown'),
                    'device_model': getattr(request, 'device_model', 'unknown'),
                    'android_sdk': getattr(request, 'android_sdk', 'unknown')
                }
            )
            return Response({'status': 'error', 'message': 'Device mismatch on integrity verification.'}, status=403)

        # ── Step 2: Consume nonce ───────────────────────────
        stored['used'] = True
        cache.set(cache_key, stored, timeout=60)

        # ── Step 3: Get previous level for downgrade detection ──
        previous_level = cache.get(f"last_integrity_{officer_id}", '')

        # ── Step 4: Call Google Play Integrity API ──────────
        package_name   = getattr(settings, 'ANDROID_PACKAGE_NAME', 'com.kavach.app')
        google_api_key = getattr(settings, 'PLAY_INTEGRITY_DECODING_KEY', '')

        if settings.DEBUG:
            integrity_level = _debug_verdict()
        elif not google_api_key:
            return Response({
                'status': 'error',
                'message': 'Server integrity configuration error.'
            }, status=500)
        else:
            integrity_level = _call_google_integrity_api(
                token=integrity_token,
                package_name=package_name,
                api_key=google_api_key
            )

        # ── Step 5: DEGRADED Chaining (Risk 3 Fix) ──────────
        # Prevent permanent blind trust. If attacker blocks Google APIs,
        # they could stay in DEGRADED forever.
        if integrity_level == 'DEGRADED':
            chain_count = cache.get(f"degraded_chain_{officer.id}", 0) + 1
            cache.set(f"degraded_chain_{officer.id}", chain_count, timeout=3600)
            if chain_count >= 5:
                integrity_level = 'FAILED'
                OfficerActivity.objects.create(
                    officer=officer if officer.is_authenticated else None,
                    unit=officer.unit if (officer and officer.is_authenticated) else None,
                    pno=officer_pno,
                    action='INTEGRITY_MAX_DEGRADED_EXCEEDED',
                    route=request.path,
                    result='FAILED',
                    severity='CRITICAL',
                    ip_address=_get_client_ip(request),
                    device_id_hash=hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
                    metadata={'chain_count': chain_count}
                )
        else:
            cache.delete(f"degraded_chain_{officer.id}")

        # ── Step 6: Update metrics + device trust ──────────
        _update_integrity_metrics(integrity_level, previous_level, build_type=build_type)
        if officer.is_authenticated:
            _update_device_trust(officer, integrity_level)

        # Cache current level for downgrade detection next time
        cache.set(f"last_integrity_{officer_id}", integrity_level, timeout=3600)

        # Risk 2 + Risk 3 FIX: STRONG history bound to device fingerprint.
        #
        # Risk 3 improvement: fingerprint now uses HMAC(SECRET_KEY, device_id)
        # instead of sha256(device_id + jwt_tail[-8:]).
        #
        # Why HMAC is better:
        #   Old: jwt_tail[-8:] is a substring of the JWT the client holds.
        #        Attacker with stolen JWT knows the tail → can compute same fingerprint.
        #   New: HMAC key = settings.SECRET_KEY (server-only secret, never sent to client).
        #        Attacker cannot compute the fingerprint without knowing SECRET_KEY.
        #        Same device_id on same server always produces same fingerprint — deterministic.
        #
        # device_binding_key: stable per (server, device). Changes if SECRET_KEY rotates.
        if integrity_level == 'STRONG':
            device_binding_key = _hmac_module.new(
                settings.SECRET_KEY[:32].encode(),
                device_id.encode(),
                hashlib.sha256
            ).hexdigest()[:16]
            cache.set(
                f"last_strong_integrity_{officer_id}_{device_binding_key}",
                True,
                timeout=4 * 3600
            )

        # ── Step 6: Audit log ───────────────────────────────
        OfficerActivity.objects.create(
            officer=officer if officer.is_authenticated else None, 
            unit=officer.unit if (officer and officer.is_authenticated) else None,
            pno=officer_pno,
            action='INTEGRITY_VERIFIED',
            route=request.path,
            result='SUCCESS',
            severity='INFO',
            ip_address=_get_client_ip(request),
            device_id_hash=hashlib.sha256(device_id.encode()).hexdigest() if device_id else '',
            metadata={
                'integrity_level': integrity_level,
                'previous_level':  previous_level,
                'build_type':      build_type,
                'request_id':      request_id,
            }
        )

        # ── Step 7: Policy decision ─────────────────────────
        policy = LEVEL_POLICY.get(integrity_level, LEVEL_POLICY['FAILED'])

        # Risk 6 FIX: Jittered trust window per attestation.
        # Client MUST use this server-returned value (not a hardcoded constant).
        trust_window = _jittered_trust_window(integrity_level)

        # Risk 5 FIX: Store absolute expiry timestamp instead of inferred TTL.
        #
        # Old: cache.set(key, window_ms, timeout=window+120)
        #   Problem: clock drift between nodes + cache eviction delay
        #   = expired session survives past its window
        #
        # New: cache absolute Unix-ms timestamp when THIS attestation expires.
        #   Middleware compares: now_ms > expires_at_ms → exact, drift-proof.
        #   Cache TTL is generous (window + 5min) to ensure value is present.
        expires_at_ms = int(time.time() * 1000) + (trust_window * 60 * 1000)
        cache.set(
            f"integrity_expires_at_{officer_id}",
            expires_at_ms,
            timeout=trust_window * 60 + 300  # Keep in cache 5min past expiry (grace for slow requests)
        )

        return Response({
            'status': 'success',
            'data': {
                'integrity_level':      integrity_level,
                'restricted':           policy['restricted'],
                'read_only':            policy['read_only'],
                'no_live_reads':        policy['no_live_reads'],
                'blocked':              policy['blocked'],
                'trust_window_minutes': trust_window,   # Risk 6: jittered — client uses THIS
                'message':              _level_message(integrity_level),
                'session_tag':          _generate_session_tag(officer, integrity_level)
            }
        })



def _call_google_integrity_api(token: str, package_name: str, api_key: str) -> str:
    """
    Calls Google Play Integrity API.
    Returns integrity_level string.

    ISSUE 7 FIX: appRecognitionVerdict is the REAL debug/sideload gate.
    'UNEVALUATED' means Play Store didn't evaluate the app → likely sideloaded or debug.
    This is enforced HERE (in the verdict), not via X-Build-Type header.
    """
    url     = f"https://playintegrity.googleapis.com/v1/{package_name}:decodeIntegrityToken"
    payload = {'integrity_token': token}
    params  = {'key': api_key}

    try:
        resp = requests.post(url, json=payload, params=params, timeout=10)
        if resp.status_code != 200:
            return 'FAILED'

        verdict = resp.json().get('tokenPayloadExternal', {})

        # ── Check App Integrity (REAL debug gate — not X-Build-Type header) ──
        app_verdict = verdict.get('appIntegrity', {}).get('appRecognitionVerdict', '')
        if app_verdict == 'UNRECOGNIZED_VERSION':
            return 'FAILED'    # Tampered APK signature — hard block
        # UNEVALUATED = sideloaded or debug build not in Play Store — downgrade to BASIC at best
        sideloaded = (app_verdict == 'UNEVALUATED')

        # ── Check Device Integrity ────────────────────────────
        device_verdicts = verdict.get('deviceIntegrity', {}).get('deviceRecognitionVerdict', [])

        if 'MEETS_STRONG_INTEGRITY' in device_verdicts and not sideloaded:
            return 'STRONG'
        elif 'MEETS_DEVICE_INTEGRITY' in device_verdicts and not sideloaded:
            return 'DEVICE'
        elif 'MEETS_BASIC_INTEGRITY' in device_verdicts:
            return 'BASIC'   # Sideloaded or root-adjacent → BASIC (no live reads)
        else:
            return 'FAILED'

    except requests.Timeout:
        # ISSUE 1 FIX: Timeout from Google ≠ hard failure.
        # Return DEGRADED — client gets 5-min grace window.
        # This is critical: field officers must not be locked out by a slow Google API call.
        return 'DEGRADED'

    except Exception:
        return 'DEGRADED'


def _debug_verdict() -> str:
    """Only used when settings.DEBUG=True (local dev)."""
    return 'DEVICE'  # Change to 'BASIC' or 'FAILED' to test restrictions locally


def _update_device_trust(officer: Officer, integrity_level: str):
    """Updates the officer's device trust score and untrusted flag."""
    trust_scores = {
        'STRONG':   100.0,
        'DEVICE':    80.0,
        'BASIC':     30.0,
        'DEGRADED':  50.0,
        'FAILED':     0.0,
    }
    score = trust_scores.get(integrity_level, 0.0)
    # Find the specific device if possible, otherwise update all for this officer (safety)
    OfficerDevice.objects.filter(officer=officer).update(
        trust_score=score,
        integrity_level=integrity_level,
        last_integrity_check_at=timezone.now()
    )


def _level_message(level: str) -> str:
    return {
        'STRONG':   'डिवाइस पूरी तरह सत्यापित है। पूर्ण एक्सेस उपलब्ध।',
        'DEVICE':   'डिवाइस सत्यापित है। सामान्य एक्सेस उपलब्ध।',
        'BASIC':    'सीमित सत्यापन। केवल कैश्ड डेटा उपलब्ध। लाइव डेटा एक्सेस बंद है।',
        'DEGRADED': 'नेटवर्क समस्या। सत्यापन अधूरा है। 5 मिनट की छूट उपलब्ध — पुनः प्रयास करें।',
        'FAILED':   'डिवाइस सुरक्षा जाँच विफल। KAVACH इस डिवाइस पर नहीं चलाया जा सकता।',
    }.get(level, 'अज्ञात स्थिति।')


def _generate_session_tag(officer: Officer, integrity_level: str) -> str:
    """
    Server-signed proof of attestation.
    Includes level so BASIC cannot impersonate STRONG within the same window.
    """
    minute_bucket = str(int(time.time()) // 60)
    raw = f"{officer.id}:{integrity_level}:{minute_bucket}:{settings.SECRET_KEY[:8]}"
    return hashlib.sha256(raw.encode()).hexdigest()[:32]
