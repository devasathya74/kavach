import os
import sys
import django
import asyncio
import time
import uuid
import threading
import requests
import json
import websockets
from concurrent.futures import ThreadPoolExecutor

# Step 1: Boot Django environment
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from django.utils import timezone
from apps.auth_app.models import Officer, OfficerProfile, OfficerDevice, OfficerCredential
from rest_framework_simplejwt.tokens import RefreshToken
from django.db import transaction, connection

TEST_PNO = "999999999"
BASE_URL = "http://127.0.0.1:8000"
WS_URL = "ws://127.0.0.1:8000"

def get_or_create_test_officer():
    """Sets up a secure, valid Officer with matching device/profile for authentic test simulation."""
    print("Setting up secure test officer...")
    from apps.auth_app.models import RankMaster, UnitMaster
    
    with transaction.atomic():
        # Setup Master records first
        default_rank, _ = RankMaster.objects.get_or_create(code="PILOT", defaults={'name': 'Pilot', 'level': 5})
        default_unit, _ = UnitMaster.objects.get_or_create(code="HQ", defaults={'name': 'Headquarters', 'type': 'HQ'})
        
        officer, created = Officer.objects.get_or_create(
            pno=TEST_PNO,
            defaults={
                'role': 'ADMIN',
                'is_active': True,
                'unit': default_unit
            }
        )
        if created or officer.role != 'ADMIN':
            officer.role = 'ADMIN'
            officer.set_password("SecureHarnessPassword123!")
            officer.save()
            
        # Setup related profile
        OfficerProfile.objects.get_or_create(
            officer=officer,
            defaults={
                'name': "Test Harness Officer",
                'rank': default_rank,
                'unit': default_unit,
                'email': 'harness@kavach.local'
            }
        )

        # Ensure credentials model exist
        OfficerCredential.objects.get_or_create(
            officer=officer,
            defaults={'device_secret': 'HARNESS_SECRET_KEY_AES256'}
        )

        # Register device binding
        device, _ = OfficerDevice.objects.get_or_create(
            officer=officer,
            device_id='TEST_DEVICE_HARNESS_ID',
            defaults={
                'device_name': 'Harness Simulator',
                'manufacturer': 'Kavach Inc',
                'android_version': '14',
                'app_version': '1.0.0',
                'status': 'ACTIVE',
                'trust_score': 100.0
            }
        )
        if device.status != 'ACTIVE':
            device.status = 'ACTIVE'
            device.save()
        
    return officer

def delete_test_officer():
    """Cleans up the test user from database."""
    print("Cleaning up test officer...")
    try:
        with transaction.atomic():
            Officer.objects.filter(pno=TEST_PNO).delete()
    except Exception as e:
        print(f"Error during cleanup: {e}")

# WebSocket Flood Test Routine
async def ws_worker(worker_id, token, stats):
    uri = f"{WS_URL}/ws/broadcast/?token={token}"
    try:
        async with websockets.connect(uri) as ws:
            stats["connected"] += 1
            # Wait inside connection to simulate persistence
            await asyncio.sleep(2.0)
            
            # Send message
            await ws.send(json.dumps({
                "type": "RAISE_HAND"
            }))
            
            # Read response
            response = await ws.recv()
            if "Connected" in response or "HAND_RAISED" in response:
                stats["success"] += 1
    except Exception as e:
        stats["failed"] += 1
        stats["errors"].append(str(e))

async def run_ws_flood(token):
    print("\n--- TEST 1: WebSocket Flood (50 Concurrent Connections) ---")
    stats = {"connected": 0, "success": 0, "failed": 0, "errors": []}
    
    tasks = [ws_worker(i, token, stats) for i in range(50)]
    start_time = time.time()
    await asyncio.gather(*tasks)
    elapsed = time.time() - start_time
    
    print(f"WebSocket Flood Completed in {elapsed:.2f}s")
    print(f"-> Connected: {stats['connected']}/50")
    print(f"-> Successful Frames: {stats['success']}/50")
    print(f"-> Failed: {stats['failed']}/50")
    if stats["failed"] > 0:
        print(f"-> Errors Encountered: {set(stats['errors'])}")
    else:
        print("-> Result: PASS (WebSockets are robust & concurrent)")

# DDoS / Rate Throttling Test Routine
def hit_endpoint(client_session, idx, results):
    # Hit User Management endpoint without auth to trigger AnonRateThrottle (5/min limit)
    url = f"{BASE_URL}/api/v2/auth/users/"
    try:
        res = client_session.get(url, timeout=2.0)
        results.append(res.status_code)
    except Exception as e:
        results.append(500)

def test_rate_limiting():
    print("\n--- TEST 2: Invalid Query / DDoS Rate Limiting Simulation ---")
    results = []
    session = requests.Session()
    
    # Fire 12 requests rapidly (Anon throttle rate is 5/min)
    with ThreadPoolExecutor(max_workers=5) as executor:
        for i in range(12):
            executor.submit(hit_endpoint, session, i, results)
            
    # Counts
    statuses = {}
    for code in results:
        statuses[code] = statuses.get(code, 0) + 1
        
    print(f"DDoS rate limiting results: {statuses}")
    if 429 in statuses:
        print(f"-> Result: PASS (AnonRateThrottle successfully blocked {statuses[429]} requests with HTTP 429)")
    else:
        print("-> Result: WARNING (Throttling not triggered. Check throttle configurations in settings.py)")

# Database Write-Lock & Timeout Simulation
def hold_db_lock_worker(event_start, event_end):
    """Holds a lock on the test officer table in a separate thread/session."""
    print("[DB-LOCK-THREAD] Booting session and initiating write-lock...")
    # Explicit new DB connection
    connection.close()
    
    with transaction.atomic():
        # lock the test user row
        list(Officer.objects.filter(pno=TEST_PNO).select_for_update())
        print("[DB-LOCK-THREAD] Lock acquired. Holding row locked for 3 seconds...")
        event_start.set()
        time.sleep(3.0)
        print("[DB-LOCK-THREAD] Releasing transaction write lock...")
    
    event_end.set()

def test_database_timeout_response(officer_id, token):
    print("\n--- TEST 3: DB Write-Lock / Query Timeout Simulation ---")
    event_start = threading.Event()
    event_end = threading.Event()
    
    # 1. Start thread to lock target officer table row
    lock_thread = threading.Thread(target=hold_db_lock_worker, args=(event_start, event_end))
    lock_thread.start()
    
    # Wait until lock is active
    event_start.wait(timeout=2.0)
    time.sleep(0.5) # ensure write-lock is held
    
    print("[MAIN-THREAD] Simulating immediate query that tries to modify locked row...")
    url = f"{BASE_URL}/api/v2/auth/users/{officer_id}/"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "X-Device-Id": "TEST_DEVICE_HARNESS_ID"
    }
    
    # Hit PATCH endpoint with timeout parameters to force wait timeout on DB lock
    start_time = time.time()
    try:
        # We simulate a DB lockout timeout. In Postgres, lock wait will block this.
        # Let's hit the server and expect a timeout or structured failure if we set short timeouts.
        # In sqlite it might raise "database is locked" immediately, which we capture as OperationalError!
        response = requests.patch(
            url, 
            json={"role": "PILOT"}, 
            headers=headers,
            timeout=5.0
        )
        elapsed = time.time() - start_time
        print(f"[MAIN-THREAD] Server replied in {elapsed:.2f}s with status {response.status_code}")
        
        # Parse output JSON schema
        data = response.json()
        print(f"[MAIN-THREAD] Payload Response: {json.dumps(data, indent=2)}")
        
        # Validate schema structure
        if "status" in data and "error" in data:
            print("-> Result: PASS (Global exception handler returned the correct standardized JSON error block)")
            if data["error"]["code"] == "DATABASE_LOCKOUT_TIMEOUT":
                print("-> Specific Sub-Result: PASS (Correctly identified Database Lockout Timeout error code!)")
        else:
            print("-> Result: WARNING (Schema did not match the required status/error format)")
            
    except Exception as e:
        print(f"[MAIN-THREAD] Request Exception: {e}")
        
    lock_thread.join()

# ASGI Exception Interception / Close WebSockets cleanly
async def test_asgi_crash_recovery(token):
    print("\n--- TEST 4: ASGI exception and connection crash interceptor validation ---")
    
    # 1. Verify invalid token is rejected with HTTP 403
    bad_uri = f"{WS_URL}/ws/broadcast/?token=INVALID_MOCK_JWT_STORM_VALUE"
    try:
        async with websockets.connect(bad_uri):
            print("-> Sub-Test 1 (Invalid Token Rejection): FAIL (Allowed connection with invalid token!)")
    except Exception as e:
        if "403" in str(e) or getattr(e, 'status_code', None) == 403:
            print("-> Sub-Test 1 (Invalid Token Rejection): PASS (Correctly rejected invalid token with HTTP 403)")
        else:
            print(f"-> Sub-Test 1 (Invalid Token Rejection): FAIL (Unexpected exception: {e})")

    # 2. Verify interceptor handles invalid JSON payload on a valid connection
    good_uri = f"{WS_URL}/ws/broadcast/?token={token}"
    try:
        async with websockets.connect(good_uri) as ws:
            # Read immediate connection confirmation frame first
            await ws.recv()
            
            # Send invalid JSON to trigger consumer exception
            await ws.send("GARBAGE_PAYLOAD_NOT_JSON")
            resp = await ws.recv()
            data = json.loads(resp)
            if data.get("status") is False and data.get("error", {}).get("code") == "WEBSOCKET_ERROR":
                print("-> Sub-Test 2 (Consumer Interceptor): PASS (Websocket interceptor returned correct standardized error frame!)")
            else:
                print(f"-> Sub-Test 2 (Consumer Interceptor): FAIL (Returned unexpected payload: {resp})")
    except Exception as e:
        print(f"-> Sub-Test 2 (Consumer Interceptor): FAIL (Exception: {e})")

# Main Harness Runner
async def main(officer_id, token):
    print("======================================================================")
    print("      KAVACH ENTERPRISE STRESS & RESILIENCE SIMULATION HARNESS        ")
    print("======================================================================\n")
    
    # 3. Run Tests
    # WS flood
    await run_ws_flood(token)
    
    # Rate limit
    test_rate_limiting()
    
    # DB lock/Timeout
    test_database_timeout_response(officer_id, token)
    
    # ASGI WebSocket Crash
    await test_asgi_crash_recovery(token)
    
    print("\n======================================================================")
    print("                    STRESS SIMULATION COMPLETED                      ")
    print("======================================================================")

if __name__ == "__main__":
    # 1. Initialize DB models & user synchronously
    officer = get_or_create_test_officer()
    try:
        # 2. Generate access token synchronously
        refresh = RefreshToken.for_user(officer)
        token = str(refresh.access_token)
        print(f"Generated secure JWT: {token[:20]}... [Valid device: TEST_DEVICE_HARNESS_ID]\n")
        
        asyncio.run(main(officer.id, token))
    finally:
        # Clean up synchronously
        delete_test_officer()
