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

def run_human_bias_attack():
    print("--- HUMAN BIAS ATTACK SIMULATION ---")
    
    # 1. Setup Target Officer (Perfect Record)
    target_pno = "111111111"
    try:
        target = Officer.objects.get(pno=target_pno)
        ds, _ = DisciplineScore.objects.get_or_create(officer=target)
        ds.score = 100
        ds.predicted_score = 100
        ds.save()
        print(f"Target: {target.name} | Score: {ds.score} (Perfect)")
    except Officer.DoesNotExist:
        print("Error: Target officer not found.")
        return

    # 2. Setup Biased Reviewer (CC)
    reviewer_pno = "222222222"
    try:
        reviewer = Officer.objects.get(pno=reviewer_pno)
        print(f"Reviewer (CC): {reviewer.name} ({reviewer.pno})")
    except Officer.DoesNotExist:
        print("Seeding CC User...")
        reviewer = Officer.objects.create_user(
            pno=reviewer_pno, 
            name="Biased Commander", 
            rank=Officer.Rank.CC, 
            unit="Pilot Unit",
            role='ADMIN'
        )

    # 3. Trigger Biased Review
    biased_score = 30
    print(f"CC submits BIASED Review Score: {biased_score}")
    
    new_reliability = DecisionCore.human_audit(reviewer, target, biased_score)
    
    # 4. Verify Results
    reliability = ReviewerReliability.objects.get(reviewer=reviewer)
    
    print("\n--- RESULTS ---")
    print(f"CC Reliability Score: {reliability.reliability_score}")
    print(f"Deviation Detected: {reliability.deviation_count > 0}")
    print(f"Bias Flag Active: {reliability.bias_flag}")
    
    if reliability.reliability_score < 1.0:
        print("\n✅ PASS: Human Bias Detected and Reliability Reduced.")
    else:
        print("\n❌ FAIL: System is blind to human bias.")

if __name__ == "__main__":
    run_human_bias_attack()
