import os
import django
import sys
import io

# Fix encoding
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# Setup Django
sys.path.append(os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.behavior.models import DisciplineScore, ReviewerReliability
from apps.auth_app.models import OfficerActivity as AuditLog
from django.db.models import Count

def generate_telemetry_report():
    print("--- KAVACH PILOT TELEMETRY REPORT ---")
    
    # 1. Observation Mode Tracking
    observation_count = DisciplineScore.objects.filter(monitoring_mode='CONTROLLED_OBSERVATION').count()
    resolution_count = DisciplineScore.objects.filter(resolution_needed=True).count()
    
    # 2. Bias Detection Tracking
    bias_flags = ReviewerReliability.objects.filter(bias_flag=True).count()
    low_trust_admins = ReviewerReliability.objects.filter(reliability_score__lt=0.7).count()
    
    # 3. Decision Integrity
    total_decisions = AuditLog.objects.filter(action__contains='DECISION').count()
    bias_alerts = AuditLog.objects.filter(action='HUMAN_BIAS_DETECTED').count()
    
    print(f"\n[SYSTEM HEALTH]")
    print(f"Total Officers in Pilot: {DisciplineScore.objects.count()}")
    print(f"Officers in Observation: {observation_count}")
    print(f"Resolution Backlog: {resolution_count}")
    
    print(f"\n[ADMIN INTEGRITY]")
    print(f"Flagged Biased Admins: {bias_flags}")
    print(f"Low Trust Reviewers (< 0.7): {low_trust_admins}")
    print(f"Bias Alerts Logged: {bias_alerts}")
    
    print(f"\n[OPERATIONAL STATUS]")
    if observation_count > 5:
        print("⚠️ WARNING: High Observation Stalling detected.")
    else:
        print("✅ Status: Healthy Flow.")

if __name__ == "__main__":
    generate_telemetry_report()
