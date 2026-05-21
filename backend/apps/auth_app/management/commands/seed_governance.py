from django.core.management.base import BaseCommand
from apps.auth_app.models import Officer, OfficerProfile, OfficerCredential, UnitMaster, RankMaster, CompanyMaster
from django.db import transaction
from django.contrib.auth.hashers import make_password

class Command(BaseCommand):
    help = 'Seeds fresh governance data and master tables'

    def handle(self, *args, **options):
        self.stdout.write("Seeding Governance Data...")
        
        with transaction.atomic():
            # 1. Master Tables (Preserve or Create)
            unit_hq, _ = UnitMaster.objects.get_or_create(code="HQ-RB", name="HQ Raebareli")
            unit_f1, _ = UnitMaster.objects.get_or_create(code="F-01", name="Field Unit 01")
            
            company_a, _ = CompanyMaster.objects.get_or_create(code="CO-A", name="Company A", unit=unit_hq)

            # Ranks (Normalized 100-20)
            rank_cmdt, _ = RankMaster.objects.get_or_create(code="COMMANDANT", name="Senanayak", level=100)
            rank_dc, _   = RankMaster.objects.get_or_create(code="DEPUTY_COMMANDANT", name="Up-Senanayak", level=90)
            rank_cc, _   = RankMaster.objects.get_or_create(code="COMPANY_COMMANDER", name="Company Commander", level=70)
            rank_pc, _   = RankMaster.objects.get_or_create(code="PLATOON_COMMANDER", name="Platoon Commander", level=50)
            rank_hc, _   = RankMaster.objects.get_or_create(code="HEAD_CONSTABLE", name="Head Constable", level=30)
            rank_ct, _   = RankMaster.objects.get_or_create(code="CONSTABLE", name="Constable", level=20)

            # 2. SEED USERS
            
            # ADMIN (Senanayak)
            self._create_user(
                pno="000000000",
                name="Senanayak",
                role="ADMIN",
                rank=rank_cmdt,
                unit=unit_hq,
                password="Admin@123"
            )

            # PILOT (Pilot Officer)
            self._create_user(
                pno="111111111",
                name="Pilot Officer",
                role="PILOT",
                rank=rank_cc,
                unit=unit_hq,
                company=company_a,
                password="Pilot@123"
            )

            # USERS (Field Personnel)
            self._create_user("222222222", "Constable A", "USER", rank_ct, unit_f1, password="User@123")
            self._create_user("333333333", "Head Constable B", "USER", rank_hc, unit_f1, password="User@123")
            self._create_user("444444444", "Platoon Commander C", "USER", rank_pc, unit_f1, password="User@123")

        self.stdout.write(self.style.SUCCESS('Governance Seeding Complete.'))

    def _create_user(self, pno, name, role, rank, unit, password, company=None):
        if Officer.objects.filter(pno=pno).exists():
            # Update existing for consistency
            officer = Officer.objects.get(pno=pno)
            officer.role = role
            officer.unit = unit
            officer.password = make_password(password)
            officer.is_staff = (role == "ADMIN")
            officer.save()
        else:
            officer = Officer.objects.create_user(
                pno=pno,
                role=role,
                unit=unit,
                password=password
            )
            officer.is_staff = (role == "ADMIN")
            officer.save()

        # Update Profile
        profile, _ = OfficerProfile.objects.update_or_create(
            officer=officer,
            defaults={
                "name": name,
                "rank": rank,
                "unit": unit,
                "company": company,
                "service_status": "ACTIVE"
            }
        )

        # Update Credentials
        OfficerCredential.objects.update_or_create(
            officer=officer,
            defaults={
                "password_hash": officer.password,
                "device_secret": f"secret_{pno}"
            }
        )
