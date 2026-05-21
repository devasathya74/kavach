import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import Officer

def create_user(pno, name, rank, unit, password, role):
    user, created = Officer.objects.get_or_create(
        pno=pno,
        defaults={
            "name": name,
            "rank": rank,
            "unit": unit,
            "role": role,
            "is_staff": (role in ['ADMIN', 'SUPERUSER'])
        }
    )
    if created or user:
        user.set_password(password)
        user.save()
        print(f"User {pno} ({role}) created/updated with password: {password}")

# Admin (HQ / Command Center)
create_user("1111/ADM", "Admin Satyajit", "SP", "Agra HQ", "admin123", "ADMIN")

# Commanding Officer (CO)
create_user("2222/CO", "CO Satyajit", "CO", "Agra North", "co123", "ADMIN")

# Standard Officer
create_user("5678/UP", "Vikram Verma", "HC", "Agra Dist", "kavach123", "USER")
