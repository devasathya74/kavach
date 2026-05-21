from django.core.management.base import BaseCommand
from apps.auth_app.models import Officer, OfficerProfile, OfficerCredential, UnitMaster, RankMaster
from django.db import transaction
import uuid

class Command(BaseCommand):
    help = 'Seeds essential pilot data (Units, Ranks, Admin Users)'

    def add_arguments(self, parser):
        parser.add_argument('--fresh', action='store_true', help='Wipe existing data before seeding')

    def handle(self, *args, **options):
        if options['fresh']:
            self.stdout.write("Wiping existing data...")
            Officer.objects.all().delete()
            UnitMaster.objects.all().delete()
            RankMaster.objects.all().delete()

        with transaction.atomic():
            # 1. Units
            unit_hq, _ = UnitMaster.objects.get_or_create(code="HQ-RB", name="HQ Raebareli")
            unit_p1, _ = UnitMaster.objects.get_or_create(code="P-01", name="Police Station 01")

            # 2. Ranks (Aligning with new authority hierarchy 100-20)
            rank_co, _ = RankMaster.objects.get_or_create(code="COMMANDANT", name="Senanayak", level=100)
            rank_pi, _ = RankMaster.objects.get_or_create(code="COMPANY_COMMANDER", name="Company Commander", level=60)
            rank_ct, _ = RankMaster.objects.get_or_create(code="CONSTABLE", name="Constable", level=20)

            # 3. Pilot/Admin Accounts
            self._create_pilot_admin("000000000", "Admin Commandant", "ADMIN", unit_hq, rank_co)
            self._create_pilot_admin("111111111", "Pilot Officer", "PILOT", unit_hq, rank_pi)
            self._create_pilot_admin("222222222", "Field Constable", "USER", unit_p1, rank_ct)

        self.stdout.write(self.style.SUCCESS('Successfully seeded pilot data.'))

    def _create_pilot_admin(self, pno, name, role, unit, rank):
        if Officer.objects.filter(pno=pno).exists():
            return
            
        officer = Officer.objects.create_user(
            pno=pno, 
            role=role, 
            unit=unit, 
            password=pno  # Pilot seed: PNO = password (e.g. 111111111)
        )
        officer.must_change_password = False
        officer.is_staff = True if role == "ADMIN" else False
        officer.save()

        OfficerProfile.objects.create(
            officer=officer,
            name=name,
            rank=rank,
            unit=unit
        )

        OfficerCredential.objects.create(
            officer=officer,
            password_hash=officer.password,
            device_secret="pilot_secret_" + pno
        )
