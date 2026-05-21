import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer

def create_officer(pno, name, rank, unit, password, email, role='USER', is_staff=False, is_superuser=False):
    try:
        user, created = Officer.objects.get_or_create(
            pno=pno,
            defaults={
                "name": name,
                "rank": rank,
                "unit": unit,
                "email": email,
                "role": role,
                "is_staff": is_staff,
                "is_superuser": is_superuser
            }
        )
        if created:
            user.set_password(password)
            user.save()
            print(f"SUCCESS: User {pno} ({role}) created successfully.")
        else:
            print(f"INFO: User {pno} already exists.")
    except Exception as e:
        print(f"ERROR: Error creating user {pno}: {str(e)}")

# 1. Admin (CO)
create_officer(
    pno="000000000",
    name="Admin Satyajit",
    rank="CO",
    unit="Agra HQ",
    password="000000000",
    email="qasw09599@gmail.com",
    role="SUPERUSER",
    is_staff=True,
    is_superuser=True
)

# 2. Pilot User
create_officer(
    pno="111111111",
    name="Pilot User",
    rank="PC",
    unit="Agra North",
    password="111111111",
    email="sathyauppolice@gmail.com",
    role="USER"
)

# Verify
print("\n--- Current Users ---")
for o in Officer.objects.all().values("pno", "name", "rank", "role"):
    print(o)
