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

from apps.auth_app.models import Officer
from apps.behavior.models import DisciplineScore, ReviewerReliability
from apps.behavior.decision_core import DecisionCore

def run_multi_attack_simulation():
    print("--- MULTI-ATTACK BATTLEFIELD SIMULATION ---")
    
    # 1. Setup Target Officer
    target_pno = "111111111"
    target = Officer.objects.get(pno=target_pno)
    ds, _ = DisciplineScore.objects.get_or_create(officer=target)
    ds.score = 100
    ds.last_arbitrated_score = 100
    ds.monitoring_mode = 'NORMAL'
    ds.save()
    
    # 2. Setup Biased Reviewer
    reviewer_pno = "222222222"
    reviewer = Officer.objects.get(pno=reviewer_pno)
    rel, _ = ReviewerReliability.objects.get_or_create(reviewer=reviewer)
    rel.reliability_score = 1.0
    rel.bias_flag = False
    rel.save()

    print(f"Target: {target.name} | Score: {ds.score}")
    print(f"Reviewer: {reviewer.name} | Reliability: {rel.reliability_score}")

    # --- SIMULATING SIMULTANEOUS ATTACKS ---
    
    # A. Biased Review (Score 30)
    biased_review_score = 30
    print(f"\n[ATTACK A] CC submits Biased Review: {biased_review_score}")
    new_rel, is_rejected = DecisionCore.human_audit(reviewer, target, biased_review_score)
    print(f"Result: Reliability dropped to {new_rel:.2f} | Review Rejected: {is_rejected}")

    # B. AI Anomaly + Low Confidence (Signals)
    signals = {
        'ai_anomaly': 60, # Moderate Anomaly (Suspicion 40)
        'human_review': biased_review_score if not is_rejected else ds.score,
        'baseline_drift': 100,
        'prediction': 100,
        'confidence': 0.5, # LOW CONFIDENCE
        'reviewer_pno': reviewer_pno
    }
    
    print("\n[ATTACK B] AI Anomaly (60) + Low Confidence (0.5) Signals incoming...")
    
    # 3. RUN ARBITRATION
    from django.conf import settings
    setattr(settings, 'KAVACH_PILOT_PHASE', 4) # Full Pilot
    
    final_score = DecisionCore.arbitrate(target, signals)
    
    # 4. Final Audit
    ds.refresh_from_db()
    print("\n--- FINAL SYSTEM STATUS ---")
    print(f"Final Discipline Score: {ds.score}")
    print(f"Monitoring Mode: {ds.monitoring_mode}")
    print(f"Resolution Needed: {ds.resolution_needed}")
    
    # Validation Logic
    fairness_pass = (ds.score == 100)
    decisiveness_pass = (ds.monitoring_mode == 'CONTROLLED_OBSERVATION')
    
    if fairness_pass and decisiveness_pass:
        print("\n🏆 MISSION SUCCESS: System remained FAIR and DECISIVE under multi-attack.")
    else:
        print("\n❌ MISSION FAILED: System vulnerabilities detected.")

if __name__ == "__main__":
    run_multi_attack_simulation()
