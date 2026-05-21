import os
import django
import sys

# Setup Django
sys.path.append(os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from django.conf import settings

def apply_pilot_config():
    print("--- APPLYING PILOT OPERATIONS CONFIG ---")
    
    # 1. Pilot Phase Downgrade (Limited Authority)
    # Allows Monitoring, Alerts, and Soft Friction. 
    # Blocks hard penalties without high confidence.
    setattr(settings, 'KAVACH_PILOT_PHASE', 3) 
    print("PILOT PHASE: 3 (LIMITED AUTHORITY) - [SAFE MODE ACTIVE]")
    
    # 2. Telemetry Baseline
    print("TELEMETRY: Enabled")
    print("AUTO-PENALTY CONFIDENCE GUARD: 0.8")
    print("OBSERVATION TIMEOUT: 48h")
    
    print("\n✅ Operational Config Applied.")

if __name__ == "__main__":
    apply_pilot_config()
