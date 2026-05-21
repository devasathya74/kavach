import uuid
import time
import logging
import hmac
import hashlib
from django.utils.deprecation import MiddlewareMixin
from django.conf import settings
from django.core.cache import cache
from django.http import JsonResponse
from kavach_backend.logging_utils import set_correlation_id, reset_correlation_id, set_user_context, reset_user_context
from kavach_backend.metrics import API_REQUESTS_TOTAL, API_REQUEST_LATENCY, EXCEPTIONS_TOTAL

logger = logging.getLogger('kavach.api')
sec_logger = logging.getLogger('kavach.security')

SENSITIVE_ROUTE_PATTERNS = getattr(settings, 'SENSITIVE_ROUTE_PATTERNS', [
    '/auth/login',
    '/auth/otp',
    '/auth/reset',
    '/auth/register',
    '/api/v2/auth/login'
])

class CorrelationIDMiddleware(MiddlewareMixin):
    """
    Middleware to generate or preserve a request Correlation ID, track
    authenticated user context, calculate API execution latency, enforce
    stale request protection, nonce-deduplication, HMAC signatures, and
    output structured API metrics.
    """
    def stale_request_protection(self, request, req_timestamp):
        """
        Validates timestamp freshness to prevent stale request execution.
        Renamed from 'replay protection' internally as timestamp alone does not prevent replays.
        """
        try:
            drift = abs(time.time() - float(req_timestamp))
            if drift > 300:
                sec_logger.warning(
                    f"Stale request rejected: timestamp drift {drift:.2f}s exceeded 300s threshold."
                )
                return JsonResponse({
                    "error": "Request expired. Clock drift limits exceeded.", 
                    "code": "REQUEST_EXPIRED"
                }, status=400)
        except ValueError:
            return JsonResponse({
                "error": "Invalid request timestamp schema.", 
                "code": "INVALID_TIMESTAMP"
            }, status=400)
        return None

    def nonce_deduplication_protection(self, request, nonce):
        """
        Prevents execution of replayed requests using cache-backed nonce verification.
        """
        nonce_key = f"nonce_{nonce}"
        if cache.get(nonce_key):
            sec_logger.warning(f"Replay attempt detected: nonce {nonce} already consumed.")
            return JsonResponse({
                "error": "Replay attack detected. Nonce already consumed.", 
                "code": "REPLAY_ATTACK"
            }, status=401)
        # Store for 5 minutes (300 seconds)
        cache.set(nonce_key, True, timeout=300)
        return None

    def verify_request_signature(self, request, signature, nonce, req_timestamp):
        """
        Validates HMAC signature on sensitive routes using SHA-256 and django SECRET_KEY.
        """
        try:
            body_bytes = request.body
        except Exception as e:
            logger.error(f"Failed to read request body for signature verification: {str(e)}")
            body_bytes = b''
            
        message = f"{request.method}{request.path}{nonce or ''}{req_timestamp or ''}".encode() + body_bytes
        expected_sig = hmac.new(settings.SECRET_KEY.encode(), message, hashlib.sha256).hexdigest()
        
        if not hmac.compare_digest(expected_sig, signature):
            sec_logger.error(f"Invalid request signature block for {request.path}")
            return JsonResponse({
                "error": "Invalid payload signature. Access denied.", 
                "code": "INVALID_SIGNATURE"
            }, status=403)
        return None

    def process_request(self, request):
        correlation_id = request.headers.get('X-Correlation-ID') or str(uuid.uuid4())
        request.correlation_id = correlation_id
        request._start_time = time.time()
        
        # User details extraction for correlation
        pno = 'anonymous'
        role = 'guest'
        if hasattr(request, 'user') and request.user.is_authenticated:
            pno = getattr(request.user, 'pno', str(request.user.id))
            role = getattr(request.user, 'role', 'user')
            
        user_str = f"{pno}:{role}"
        request._correlation_token = set_correlation_id(correlation_id)
        request._user_token = set_user_context(user_str)

        # 1. Stale request protection
        req_timestamp = request.headers.get('X-Request-Timestamp')
        if req_timestamp:
            resp = self.stale_request_protection(request, req_timestamp)
            if resp:
                return resp

        # 2. Nonce deduplication protection
        nonce = request.headers.get('X-Request-Nonce')
        if nonce:
            resp = self.nonce_deduplication_protection(request, nonce)
            if resp:
                return resp

        # 3. HMAC Signature (Sensitive Routes Only)
        if any(pattern in request.path for pattern in SENSITIVE_ROUTE_PATTERNS):
            signature = request.headers.get('X-Request-Signature')
            if not signature:
                # Bypass in DEBUG mode if signature is not present AND KAVACH_PILOT_MODE is True
                # (to facilitate developer testing / staging tools without breaking raw requests)
                if not (settings.DEBUG and getattr(settings, 'KAVACH_PILOT_MODE', False)):
                    sec_logger.warning(f"Missing signature for sensitive route: {request.path}")
                    return JsonResponse({
                        "error": "Missing security signature for privileged route.", 
                        "code": "MISSING_SIGNATURE"
                    }, status=403)
            else:
                resp = self.verify_request_signature(request, signature, nonce, req_timestamp)
                if resp:
                    return resp

    def process_response(self, request, response):
        if hasattr(request, '_start_time'):
            duration = time.time() - request._start_time
            response['X-Correlation-ID'] = getattr(request, 'correlation_id', '-')
            response['X-Response-Time'] = f"{duration:.4f}s"
            
            # Log exact latency metrics for API audits
            logger.info(
                f"API Request: {request.method} {request.path} returned {response.status_code} in {duration:.4f}s"
            )
            
            # Record Prometheus Metrics
            try:
                API_REQUESTS_TOTAL.labels(method=request.method, path=request.path, status=response.status_code).inc()
                API_REQUEST_LATENCY.labels(method=request.method, path=request.path).observe(duration)
            except Exception:
                pass
            
        if hasattr(request, '_correlation_token'):
            reset_correlation_id(request._correlation_token)
        if hasattr(request, '_user_token'):
            reset_user_context(request._user_token)
        return response

    def process_exception(self, request, exception):
        try:
            EXCEPTIONS_TOTAL.labels(exception=type(exception).__name__).inc()
        except Exception:
            pass
        if hasattr(request, '_correlation_token'):
            reset_correlation_id(request._correlation_token)
        if hasattr(request, '_user_token'):
            reset_user_context(request._user_token)
