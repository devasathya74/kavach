import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer, OfficerProfile, OfficerCredential, UnitMaster, RankMaster

def setup():
    # 1. Unit
    unit, _ = UnitMaster.objects.get_or_create(code="HQ-01", defaults={"name": "KAVACH HQ"})
    
    # 2. Rank
    rank_co, _ = RankMaster.objects.get_or_create(code="CO", defaults={"name": "Commanding Officer", "level": 10})
    
    # 3. Create Admin (PNO: admin)
    pno = "admin"
    password = "admin" # Default for pilot, user should change it
    
    officer, created = Officer.objects.get_or_create(
        pno=pno,
        defaults={
            "role": "COMMANDING_OFFICER",
            "unit": unit,
            "is_staff": True,
            "is_superuser": True
        }
    )
    officer.set_password(password)
    officer.save()
    
    # 4. Profile
    OfficerProfile.objects.update_or_create(
        officer=officer,
        defaults={
            "name": "KAVACH System Admin",
            "rank": rank_co,
            "unit": unit,
            "service_status": "ACTIVE"
        }
    )
    
    # 5. Credentials
    OfficerCredential.objects.get_or_create(officer=officer)
    
    print(f"SUCCESS: Admin created with PNO: {pno} and Password: {password}")

if __name__ == "__main__":
    setup()
