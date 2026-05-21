import asyncio
import json
import urllib.request
import urllib.error
import websockets

# Configurations
BASE_URL = "http://127.0.0.1:8000"
WS_LOCAL_URL = "ws://127.0.0.1:8000/ws/broadcast/"
WS_TUNNEL_URL = "wss://api.pmsraebareli.online/ws/broadcast/"

async def test_connection():
    print("--- 1. Authenticating via /api/v1/login/ ---")
    payload = {
        "pno": "CO123",
        "password": "Kavach@123",
        "device_id": "test_device_token_auth"
    }
    
    # Run urllib requests in a thread to keep it async friendly
    def do_login():
        req = urllib.request.Request(
            f"{BASE_URL}/api/v1/login/",
            data=json.dumps(payload).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        with urllib.request.urlopen(req) as response:
            return response.status, response.read().decode('utf-8')

    try:
        status_code, body = await asyncio.to_thread(do_login)
        print(f"Login Response Code: {status_code}")
        data = json.loads(body)
        token = data.get("token")
        if not token:
            print("Failed to retrieve token from response.")
            return
        print("Successfully authenticated! JWT Access Token received.")
    except urllib.error.HTTPError as e:
        print(f"Login failed (HTTPError): {e.code} - {e.read().decode('utf-8')}")
        return
    except Exception as e:
        print(f"Failed to authenticate: {e}")
        return

    # Local WS Test
    local_uri = f"{WS_LOCAL_URL}?token={token}"
    print(f"\n--- 2. Connecting to Local WebSocket: {local_uri} ---")
    try:
        async with websockets.connect(local_uri) as ws:
            print("Connected to Local WebSocket successfully!")
            # Read first message (should be the greeting message from BroadcastConsumer)
            msg = await ws.recv()
            print(f"Received from local WS: {msg}")
            
            # Send a test keepalive/message
            await ws.send(json.dumps({"type": "RAISE_HAND"}))
            print("Sent RAISE_HAND action to local WS.")
            
            # Read broadcast result
            response_msg = await ws.recv()
            print(f"Received response from local WS: {response_msg}")
    except Exception as e:
        print(f"Local WebSocket connection failed: {e}")

    # Tunnel WS Test
    tunnel_uri = f"{WS_TUNNEL_URL}?token={token}"
    print(f"\n--- 3. Connecting to Tunnel WebSocket: {tunnel_uri} ---")
    try:
        async with websockets.connect(tunnel_uri) as ws:
            print("Connected to Tunnel WebSocket successfully!")
            msg = await ws.recv()
            print(f"Received from tunnel WS: {msg}")
            
            # Send a test keepalive/message
            await ws.send(json.dumps({"type": "RAISE_HAND"}))
            print("Sent RAISE_HAND action to tunnel WS.")
            
            # Read broadcast result
            response_msg = await ws.recv()
            print(f"Received response from tunnel WS: {response_msg}")
    except Exception as e:
        print(f"Tunnel WebSocket connection failed: {e}")
        print("Attempting connection with direct Cloudflare IP fallback (DNS cache bypass)...")
        import ssl
        ssl_context = ssl.create_default_context()
        try:
            async with websockets.connect(
                tunnel_uri,
                host="104.21.65.29",
                port=443,
                ssl=ssl_context
            ) as ws:
                print("Connected to Tunnel WebSocket via direct Cloudflare IP successfully!")
                msg = await ws.recv()
                print(f"Received from tunnel WS: {msg}")
                
                # Send a test keepalive/message
                await ws.send(json.dumps({"type": "RAISE_HAND"}))
                print("Sent RAISE_HAND action to tunnel WS.")
                
                # Read broadcast result
                response_msg = await ws.recv()
                print(f"Received response from tunnel WS: {response_msg}")
        except Exception as fallback_e:
            print(f"Fallback direct Cloudflare IP connection failed: {fallback_e}")

if __name__ == "__main__":
    asyncio.run(test_connection())
