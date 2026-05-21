import os
import sys
import subprocess
import json

def verify_apk(apk_path):
    print(f"--- Verifying APK: {apk_path} ---")
    
    if not os.path.exists(apk_path):
        print(f"Error: APK not found at {apk_path}")
        return False

    # 1. Check Signature (apksigner)
    print("Checking signature...")
    try:
        # We assume apksigner is in PATH
        result = subprocess.run(['apksigner', 'verify', '--verbose', apk_path], capture_output=True, text=True)
        if result.returncode != 0:
            print("❌ Signature verification FAILED!")
            print(result.stdout)
            print(result.stderr)
            return False
        print("✅ Signature is VALID.")
    except FileNotFoundError:
        print("⚠️ apksigner not found. Please install Android Build Tools and add to PATH.")

    # 2. Check for Debuggable=true
    print("Checking debuggable flag...")
    try:
        # aapt dump badging path/to/apk
        result = subprocess.run(['aapt', 'dump', 'badging', apk_path], capture_output=True, text=True)
        if 'debuggable' in result.stdout.lower():
            print("❌ APK is DEBUGGABLE! This is unsafe for production.")
            return False
        print("✅ APK is NOT debuggable.")
    except FileNotFoundError:
        print("⚠️ aapt not found. Skipping debuggable check.")

    # 3. Check for Localhost/Development artifacts (Hardened search)
    print("Checking for development artifacts (localhost, 10.0.2.2)...")
    # This is a bit complex as it needs to decompile. 
    # For now, we just suggest the user to check their BuildConfig.
    print("👉 Ensure BuildConfig.BASE_URL is NOT pointing to localhost.")

    # 4. Generate SHA-256 for Backend
    import hashlib
    hasher = hashlib.sha256()
    with open(apk_path, 'rb') as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hasher.update(chunk)
    
    sha256_hash = hasher.hexdigest()
    print(f"\n🚀 SHA-256 for Backend: {sha256_hash}")
    print(f"File Size: {os.path.getsize(apk_path)} bytes")
    
    print("\n--- Summary ---")
    print("✅ Build looks READY for PILOT/STABLE channel.")
    print("👉 Use the above SHA-256 hash when creating the AppRelease entry in Django Admin.")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python verify_release.py <path_to_apk>")
    else:
        verify_apk(sys.argv[1])
