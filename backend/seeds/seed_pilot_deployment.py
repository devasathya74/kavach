import os
import django
import sys

# Setup Django
sys.path.append(os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer
from apps.behavior.models import DisciplineScore, ReviewerReliability

def seed_pilot_deployment():
    print("--- SEEDING PILOT DEPLOYMENT (20 Users, 3 CCs) ---")
    
    # 1. Seed Commanders (CCs)
    cc_pnos = ["CC-1001", "CC-1002", "CC-1003"]
    for pno in cc_pnos:
        user, created = Officer.objects.get_or_create(
            pno=pno,
            defaults={
                'name': f"Commander {pno}",
                'rank': Officer.Rank.CC,
                'unit': "Pilot Battalion",
                'role': 'ADMIN'
            }
        )
        if created:
            user.set_password("pilot@123")
            user.save()
            ReviewerReliability.objects.get_or_create(reviewer=user)
            print(f"Created CC: {pno}")

    # 2. Seed Officers (Users)
    for i in range(1, 21):
        pno = f"OFF-{1000 + i}"
        user, created = Officer.objects.get_or_create(
            pno=pno,
            defaults={
                'name': f"Officer {pno}",
                'rank': Officer.Rank.CONSTABLE,
                'unit': "Pilot Battalion",
                'role': 'PILOT'
            }
        )
        if created:
            user.save()
            DisciplineScore.objects.get_or_create(officer=user)
            print(f"Created Officer: {pno}")

    print("\n[SUCCESS] Pilot Seeding Complete.")

if __name__ == "__main__":
    seed_pilot_deployment()
