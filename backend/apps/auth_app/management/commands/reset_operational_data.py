from django.core.management.base import BaseCommand
from django.db import connection
from apps.auth_app.models import Officer, OfficerProfile, OfficerCredential, OfficerDevice, OtpRecord
from apps.auth_app.models.governance import OfficerActivity, OverrideRequest, DraftChange
from django.core.management import call_command

class Command(BaseCommand):
    help = 'Wipes operational data but preserves master tables'

    def handle(self, *args, **options):
        self.stdout.write("Wiping Operational Data (Preserving Masters)...")
        
        # 1. Clear Governance & Activity Logs
        DraftChange.objects.all().delete()
        OverrideRequest.objects.all().delete()
        OfficerActivity.objects.all().delete()
        OtpRecord.objects.all().delete()
        
        # 2. Clear Users & Profiles
        OfficerDevice.objects.all().delete()
        OfficerProfile.objects.all().delete()
        OfficerCredential.objects.all().delete()
        Officer.objects.all().delete()

        self.stdout.write(self.style.SUCCESS("Operational Data Wiped."))
        
        # 3. Call Seeding
        call_command('seed_governance')
