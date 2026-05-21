import os
import django
import uuid

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer, UnitMaster

def bootstrap_admin():
    # Retrieve password from environment
    password = os.getenv("KAVACH_BOOTSTRAP_PASSWORD")
    if not password:
        print("CRITICAL: KAVACH_BOOTSTRAP_PASSWORD environment variable not set.")
        print("Skipping superuser creation/update to prevent insecure defaults.")
        return

    # 1. Ensure Unit exists
    unit, created = UnitMaster.objects.get_or_create(
        code="HQ-01",
        defaults={"name": "KAVACH Headquarters"}
    )
    if created:
        print(f"Created Unit: {unit.name}")

    # 2. Create Superuser
    pno = "CO-001"
    if not Officer.objects.filter(pno=pno).exists():
        Officer.objects.create_superuser(
            pno=pno,
            unit=unit,
            password=password
        )
        print(f"Created Superuser: {pno} (using env password)")
    else:
        print(f"Superuser {pno} already exists. Skipping creation.")

if __name__ == "__main__":
    bootstrap_admin()
