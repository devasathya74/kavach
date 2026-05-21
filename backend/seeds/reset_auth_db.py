import os
import django
from django.contrib.auth.hashers import make_password

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer, OfficerProfile, OfficerCredential, OfficerDevice, UnitMaster, RankMaster

def reset_db():
    print("Clearing authentication tables...")
    OfficerDevice.objects.all().delete()
    OfficerCredential.objects.all().delete()
    OfficerProfile.objects.all().delete()
    Officer.objects.all().delete()

    print("Setting up masters...")
    unit, _ = UnitMaster.objects.get_or_create(code="HQ-01", defaults={"name": "Headquarters"})
    
    # Ranks
    rank_co, _ = RankMaster.objects.get_or_create(code="CO", defaults={"name": "Commanding Officer", "level": 10})
    rank_pilot, _ = RankMaster.objects.get_or_create(code="PILOT", defaults={"name": "Pilot / Admin", "level": 5})
    rank_user, _ = RankMaster.objects.get_or_create(code="CONST", defaults={"name": "Constable", "level": 1})

    def create_account(pno, name, role, rank, password):
        actual_role = "COMMANDING_OFFICER" if role == "ADMIN" else role
        
        # FIX: Set password directly on the Officer model
        officer = Officer.objects.create(
            pno=pno,
            role=actual_role,
            unit=unit
        )
        officer.set_password(password)
        officer.save()

        # Keep legacy field updated just in case
        import secrets
        OfficerCredential.objects.create(
            officer=officer,
            password_hash=officer.password,
            device_secret=secrets.token_urlsafe(32)
        )

        OfficerProfile.objects.create(
            officer=officer,
            name=name,
            rank=rank,
            unit=unit,
            service_status='ACTIVE'
        )
        print(f"Created: {pno} | {actual_role} | Password: {password}")

    print("Creating numeric deterministic users with correct password hashing...")
    create_account("000000000", "Command Officer", "COMMANDING_OFFICER", rank_co, "000000000")
    create_account("111111111", "Pilot Officer", "PILOT", rank_pilot, "111111111")
    create_account("222222222", "Field Officer", "USER", rank_user, "222222222")

    print("\nVERIFICATION:")
    for o in Officer.objects.all():
        print(f"PNO: {o.pno} | Password Hash: {o.password[:20]}...")

if __name__ == "__main__":
    reset_db()
