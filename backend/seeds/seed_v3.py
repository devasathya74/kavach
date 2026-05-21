import os
import django
import sys

# Setup Django
sys.path.append(os.getcwd())
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import RankMaster, UnitMaster, Officer, OfficerProfile, OfficerCredential

def seed_master_data():
    print("Seeding Master Data...")
    
    # 1. Units
    hq, _ = UnitMaster.objects.get_or_create(code='HQ', defaults={'name': 'Headquarters Battalion'})
    bt1, _ = UnitMaster.objects.get_or_create(code='BT1', defaults={'name': '1st Battalion'})
    
    # 2. Ranks
    RankMaster.objects.get_or_create(code='CO', defaults={'name': 'Commanding Officer', 'level': 100})
    RankMaster.objects.get_or_create(code='PILOT', defaults={'name': 'Pilot / Admin', 'level': 80})
    RankMaster.objects.get_or_create(code='INSP', defaults={'name': 'Inspector', 'level': 60})
    RankMaster.objects.get_or_create(code='SI', defaults={'name': 'Sub-Inspector', 'level': 50})
    RankMaster.objects.get_or_create(code='CONST', defaults={'name': 'Constable', 'level': 10})
    
    # 3. Create CO User
    co_pno = 'CO123'
    if not Officer.objects.filter(pno=co_pno).exists():
        co = Officer.objects.create_superuser(pno=co_pno, unit=hq, password='password123')
        OfficerProfile.objects.create(
            officer=co,
            name='Commanding Officer Alpha',
            rank=RankMaster.objects.get(code='CO'),
            unit=hq
        )
        OfficerCredential.objects.create(
            officer=co,
            device_secret='co_secret_key_v3'
        )
        print(f"CO User Created: {co_pno}")
    
    # 4. Create Pilot User
    pilot_pno = 'PILOT789'
    if not Officer.objects.filter(pno=pilot_pno).exists():
        pilot = Officer.objects.create_user(pno=pilot_pno, unit=bt1, role='PILOT', password='password123')
        OfficerProfile.objects.create(
            officer=pilot,
            name='Pilot Beta',
            rank=RankMaster.objects.get(code='PILOT'),
            unit=bt1
        )
        OfficerCredential.objects.create(
            officer=pilot,
            device_secret='pilot_secret_key_v3'
        )
        print(f"Pilot User Created: {pilot_pno}")

    print("Seeding Complete.")

if __name__ == "__main__":
    seed_master_data()
