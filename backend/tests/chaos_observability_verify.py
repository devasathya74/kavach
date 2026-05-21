import os
import sys
import time
import django
import hmac
import hashlib
import asyncio

# ── Bootstrap Django Environment ──────────────────────────────────────────────
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from django.conf import settings
from django.core.cache import cache
from django.test import RequestFactory
from django.http import JsonResponse
from kavach_backend.health_views import metrics_view
from kavach_backend.middleware_new import CorrelationIDMiddleware
from kavach_backend.resilience import CircuitBreaker, CircuitBreakerOpenException
from apps.auth_app.services.auth_service import AuthService
from apps.auth_app.models import Officer

# Colors for clean terminal execution logging
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
RESET = "\033[0m"

def print_result(name, passed, detail=""):
    status_str = f"{GREEN}[PASS]{RESET}" if passed else f"{RED}[FAIL]{RESET}"
    print(f" {status_str} {CYAN}{name}{RESET} {detail}")

class KavachVerificationSuite:
    def __init__(self):
        self.rf = RequestFactory()
        # Clean cache before starting
        cache.clear()
        
    def run_all(self):
        print(f"\n{YELLOW}========================================================================")
        print(f"  KAVACH RESILIENCE & HARDENED OBSERVABILITY VERIFICATION SUITE")
        print(f"========================================================================{RESET}\n")
        
        self.test_metrics_endpoint_gating()
        self.test_stale_request_protection()
        self.test_nonce_deduplication_protection()
        self.test_hmac_signature_verification()
        self.test_circuit_breaker_behavior()
        self.test_multivector_lockout_system()
        self.test_sandboxed_chaos_routes()
        
        print(f"\n{YELLOW}========================================================================{RESET}\n")

    def test_metrics_endpoint_gating(self):
        print(f"{YELLOW}--- 1. SECURE TELEMETRY SCRAPER GATING TEST ---{RESET}")
        
        # Test Case 1.1: Request without Bearer Token
        req = self.rf.get('/metrics/')
        resp = metrics_view(req)
        p1 = resp.status_code == 403
        print_result("Access Denied on Unauthenticated Scan", p1, f"(Returned {resp.status_code})")
        
        # Test Case 1.2: Request with Invalid Token
        req = self.rf.get('/metrics/', HTTP_AUTHORIZATION="Bearer WrongToken123")
        resp = metrics_view(req)
        p2 = resp.status_code == 403
        print_result("Access Denied on Malformed Bearer Credentials", p2, f"(Returned {resp.status_code})")
        
        # Test Case 1.3: Successful authenticated scrape
        token = getattr(settings, 'METRICS_TOKEN', 'KavachSecureMetricsToken2026!')
        req = self.rf.get('/metrics/', HTTP_AUTHORIZATION=f"Bearer {token}")
        resp = metrics_view(req)
        p3 = resp.status_code == 200 and b"kavach_" in resp.content
        print_result("Successful Authenticated Metrics Extraction", p3, f"(Payload contains 'kavach_' indicators)")

    def test_stale_request_protection(self):
        print(f"\n{YELLOW}--- 2. API STALE-REQUEST PROTECTION TEST ---{RESET}")
        middleware = CorrelationIDMiddleware(lambda r: JsonResponse({"status": "ok"}))
        
        # Test Case 2.1: Expired Timestamp Drift (>300 seconds)
        expired_ts = str(time.time() - 350)
        req = self.rf.get('/api/v1/auth/profile/', HTTP_X_REQUEST_TIMESTAMP=expired_ts)
        resp = middleware.process_request(req)
        p1 = resp is not None and resp.status_code == 400 and b"REQUEST_EXPIRED" in resp.content
        print_result("Rejection of Clock-Drifted Stale Request (T -350s)", p1, f"(Returned {resp.status_code if resp else 'Passed through'})")

        # Test Case 2.2: Fresh Timestamp
        fresh_ts = str(time.time())
        req = self.rf.get('/api/v1/auth/profile/', HTTP_X_REQUEST_TIMESTAMP=fresh_ts)
        resp = middleware.process_request(req)
        p2 = resp is None # Passes through
        print_result("Acceptance of Synchronized Fresh Request (T +0s)", p2, "(Passed through successfully)")

    def test_nonce_deduplication_protection(self):
        print(f"\n{YELLOW}--- 3. NONCE DEDUPLICATION REPLAY PREVENTION TEST ---{RESET}")
        middleware = CorrelationIDMiddleware(lambda r: JsonResponse({"status": "ok"}))
        
        nonce = "test-unique-nonce-12345"
        fresh_ts = str(time.time())
        
        # First attempt: should succeed and write to cache
        req1 = self.rf.post('/api/v1/auth/profile/', HTTP_X_REQUEST_TIMESTAMP=fresh_ts, HTTP_X_REQUEST_NONCE=nonce)
        resp1 = middleware.process_request(req1)
        p1 = resp1 is None
        print_result("Acceptance of Original Execution (Nonce Recorded)", p1, "(Passed through successfully)")
        
        # Second attempt: same nonce -> must be rejected
        req2 = self.rf.post('/api/v1/auth/profile/', HTTP_X_REQUEST_TIMESTAMP=fresh_ts, HTTP_X_REQUEST_NONCE=nonce)
        resp2 = middleware.process_request(req2)
        p2 = resp2 is not None and resp2.status_code == 401 and b"REPLAY_ATTACK" in resp2.content
        print_result("Mitigation of Intercepted Replay Attack (Duplicate Nonce Rejected)", p2, f"(Returned {resp2.status_code if resp2 else 'Passed through'})")

    def test_hmac_signature_verification(self):
        print(f"\n{YELLOW}--- 4. HMAC-SHA256 SIGNATURE VALIDATION TEST ---{RESET}")
        
        # Override KAVACH_PILOT_MODE to False to enforce signature check even in DEBUG mode
        original_pilot_mode = getattr(settings, 'KAVACH_PILOT_MODE', False)
        settings.KAVACH_PILOT_MODE = False
        
        middleware = CorrelationIDMiddleware(lambda r: JsonResponse({"status": "ok"}))
        
        sensitive_path = '/api/v1/auth/login/'
        timestamp = str(time.time())
        body = b'{"pno":"1001","password":"test"}'
        
        # Test Case 4.1: Missing signature header
        nonce1 = "secure-sig-nonce-1"
        req1 = self.rf.post(sensitive_path, data=body, content_type='application/json',
                            HTTP_X_REQUEST_TIMESTAMP=timestamp, HTTP_X_REQUEST_NONCE=nonce1)
        resp1 = middleware.process_request(req1)
        p1 = resp1 is not None and resp1.status_code == 403 and b"MISSING_SIGNATURE" in resp1.content
        print_result("Rejection of Privileged Route Access with Missing Signature", p1, f"(Returned {resp1.status_code if resp1 else 'Passed through'})")
        
        # Test Case 4.2: Invalid/Tampered Signature
        nonce2 = "secure-sig-nonce-2"
        req2 = self.rf.post(sensitive_path, data=body, content_type='application/json',
                            HTTP_X_REQUEST_TIMESTAMP=timestamp, HTTP_X_REQUEST_NONCE=nonce2,
                            HTTP_X_REQUEST_SIGNATURE="tampered_hex_sig_here")
        resp2 = middleware.process_request(req2)
        p2 = resp2 is not None and resp2.status_code == 403 and b"INVALID_SIGNATURE" in resp2.content
        print_result("Mitigation of Payload Tampering (Signature Invalidated)", p2, f"(Returned {resp2.status_code if resp2 else 'Passed through'})")

        # Test Case 4.3: Correct signature validation
        nonce3 = "secure-sig-nonce-3"
        message = f"POST{sensitive_path}{nonce3}{timestamp}".encode() + body
        valid_sig = hmac.new(settings.SECRET_KEY.encode(), message, hashlib.sha256).hexdigest()
        
        req3 = self.rf.post(sensitive_path, data=body, content_type='application/json',
                            HTTP_X_REQUEST_TIMESTAMP=timestamp, HTTP_X_REQUEST_NONCE=nonce3,
                            HTTP_X_REQUEST_SIGNATURE=valid_sig)
        resp3 = middleware.process_request(req3)
        p3 = resp3 is None
        print_result("Access Granted to Privileged Route with Valid Cryptographic Signature", p3, "(Signature verified successfully)")
        
        # Restore original pilot mode setting
        settings.KAVACH_PILOT_MODE = original_pilot_mode

    def test_circuit_breaker_behavior(self):
        print(f"\n{YELLOW}--- 5. STANDARDIZED CIRCUIT BREAKER BULKHEAD TEST ---{RESET}")
        
        cb = CircuitBreaker(name="test_postgres_outage", failure_threshold=3, recovery_timeout=1)
        
        @cb
        async def call_database_simulated(success=True):
            if not success:
                raise ConnectionError("PostgreSQL connection lost!")
            return "ok"

        # Verify initial CLOSED state
        p1 = cb.state == "CLOSED"
        print_result("Initial State Set to CLOSED", p1, f"(State: {cb.state})")
        
        # Trigger failures up to threshold
        loop = asyncio.get_event_loop()
        for i in range(3):
            try:
                loop.run_until_complete(call_database_simulated(success=False))
            except ConnectionError:
                pass
                
        p2 = cb.state == "OPEN"
        print_result("Circuit Breaker Tripped to OPEN State on Failure Threshold Met", p2, f"(State: {cb.state})")
        
        # Subsequent requests must be fast-failed
        fast_failed = False
        try:
            loop.run_until_complete(call_database_simulated(success=True))
        except CircuitBreakerOpenException:
            fast_failed = True
            
        print_result("Fast-Failing Calls without Dependency Execution", fast_failed, f"(CircuitBreakerOpenException Caught)")
        
        # Wait for recovery timeout
        time.sleep(1.2)
        
        # Next call should enter HALF-OPEN state and try
        res = loop.run_until_complete(call_database_simulated(success=True))
        p3 = res == "ok" and cb.state == "CLOSED"
        print_result("Downstream Recovery: Re-entered CLOSED state after successful Half-Open Trial", p3, f"(State: {cb.state})")

    def test_multivector_lockout_system(self):
        print(f"\n{YELLOW}--- 6. MULTI-VECTOR EXPONENTIAL LOCKOUT TEST ---{RESET}")
        
        from apps.auth_app.models.auth import UnitMaster, Officer
        
        pno = "LOCK_1001"
        password = "WrongPassword999"
        device_id = "test-mobile-uuid"
        client_ip = "192.168.1.100"
        
        # Ensure UnitMaster exists
        unit, _ = UnitMaster.objects.get_or_create(code="HQ_UNIT", defaults={"name": "Headquarters"})
        
        # Ensure testing Officer exists
        officer, created = Officer.objects.get_or_create(pno=pno, defaults={
            "role": "OFFICER",
            "is_active": True,
            "unit": unit
        })
        officer.set_password("CorrectKavach@123")
        officer.save()
        
        # Clear lockout cache records
        cache.delete(f"fails_user_{pno}")
        cache.delete(f"lock_user_{pno}_dev_{device_id}")
        cache.delete(f"fails_ip_{client_ip}")
        cache.delete(f"lock_ip_{client_ip}")
        cache.delete(f"fails_subnet_192.168.1")
        cache.delete(f"lock_subnet_192.168.1")
        
        # ── Brute Force Attempts ──
        for i in range(5):
            res, err = AuthService.login(pno, password, device_id, client_ip=client_ip)
            
        p1 = err == "Invalid password" or "suspended" in err
        
        # Sixth attempt: must be locked out of the device
        res, err_locked = AuthService.login(pno, password, device_id, client_ip=client_ip)
        p2 = err_locked == "Authentication temporarily suspended due to security threshold lockouts."
        print_result("Lockout Applied to Device-User Vector after 5 consecutive failures", p2, f"(Error: '{err_locked}')")

        # ── Group/Subnet lockout verification ──
        subnet_key = "fails_subnet_192.168.1"
        fails_count = cache.get(subnet_key, 0)
        p3 = fails_count == 5 # 5 failed attempts recorded before lockout
        print_result("Localized Threat Detection: Group Subnet /24 Failure Count Incremented", p3, f"(Subnet failures: {fails_count})")
        
        # Clean up database entry
        officer.delete()

    def test_sandboxed_chaos_routes(self):
        print(f"\n{YELLOW}--- 7. SANDBOXED CHAOS ROUTING SECURITY TEST ---{RESET}")
        
        # Verify Production Safety configuration
        from django.conf import settings
        prod_safety = not settings.DEBUG and getattr(settings, 'KAVACH_CHAOS_MODE', False)
        p1 = not prod_safety # Must be False
        print_result("Production Gated Sandbox Compliance (Chaos Mode disabled in Prod)", p1, f"(settings.DEBUG={settings.DEBUG}, KAVACH_CHAOS_MODE={getattr(settings, 'KAVACH_CHAOS_MODE', False)})")


if __name__ == "__main__":
    suite = KavachVerificationSuite()
    suite.run_all()
