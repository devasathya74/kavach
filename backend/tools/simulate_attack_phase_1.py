import os
import django
import sys
import io

# Fix encoding for Hindi characters in terminal
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# Setup Django
sys.path.append(os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from django.conf import settings
setattr(settings, 'KAVACH_PILOT_PHASE', 4)

from apps.auth_app.models import Officer
from apps.behavior.models import BehaviorEvent, DisciplineScore
from apps.behavior.anomaly import AnomalyEngine
from django.utils import timezone

def run_attack_phase_1():
    print("--- PHASE 1: DATA INTEGRITY ATTACK ---")
    
    # 1. Setup Officer (Pilot)
    pno = "111111111"
    try:
        officer = Officer.objects.get(pno=pno)
        print(f"Targeting Officer: {officer.name} ({officer.pno})")
    except Officer.DoesNotExist:
        print(f"Error: Officer {pno} not found. Please run seed script first.")
        return

    # 2. Inject Fake Event
    # read_time = 2s, video_duration = 300s
    event = BehaviorEvent.objects.create(
        officer=officer,
        event_type='TRAINING_COMPLETE',
        timestamp_ms=int(timezone.now().timestamp() * 1000),
        metadata={
            'duration_seconds': 2,
            'expected_seconds': 300,
            'training_id': 1
        }
    )
    print(f"Injected Fake Event: {event.event_type} | Duration: 2s | Expected: 300s")

    # 3. Run Anomaly Engine
    engine = AnomalyEngine(officer)
    engine.evaluate_all_patterns(metadata=event.metadata)
    print("Anomaly Engine evaluation complete.")

    # 4. Verify Result
    officer.refresh_from_db()
    ds = DisciplineScore.objects.get(officer=officer)
    
    print("\n--- RESULTS ---")
    print(f"Suspicion Score: {engine.suspicion_score}")
    print(f"Detected Reasons: {engine.reasons}")
    print(f"Risk Level: {ds.risk_level}")
    print(f"Final Discipline Score: {ds.score}")
    print(f"Confidence Score: {ds.anomaly_score / 100}")
    
    # Verification Logic
    if any("Fast Reading" in r or "बहुत तेज़" in r for r in engine.reasons):
        print("\n✅ PASS: Anomaly triggered with correct reason.")
    else:
        print("\n❌ FAIL: System is blind to fast reading.")

if __name__ == "__main__":
    run_attack_phase_1()
