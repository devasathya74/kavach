from django.core.management.base import BaseCommand
from django.utils import timezone
from apps.auth_app.models.infrastructure import EventOutbox, OperationalEvent
# Assuming other models like NotificationDelivery and RealtimeSession exist in similar packages
from datetime import timedelta

from django.db import connection

class Command(BaseCommand):
    help = 'Enforce mission-critical data retention and archival policies.'

    def handle(self, *args, **options):
        now = timezone.now()
        self.stdout.write(f"--- Starting Operational Cleanup at {now} ---")

        # 1. Event Outbox (Retention: 7 Days)
        outbox_cutoff = now - timedelta(days=7)
        outbox_deleted, _ = EventOutbox.objects.filter(
            created_at__lt=outbox_cutoff,
            status__in=['PROCESSED', 'FAILED']
        ).delete()
        self.stdout.write(f"Purged {outbox_deleted} processed/failed EventOutbox entries.")

        # 2. Database Hygiene (VACUUM discipline)
        self.stdout.write("Executing VACUUM ANALYZE for storage reclamation...")
        with connection.cursor() as cursor:
            cursor.execute("VACUUM ANALYZE")
            
            # 3. Bloat Check (Monitor Dead Tuples)
            self.stdout.write("--- Bloat Check (Dead Tuples) ---")
            cursor.execute("""
                SELECT relname, n_dead_tup 
                FROM pg_stat_user_tables 
                ORDER BY n_dead_tup DESC 
                LIMIT 5;
            """)
            bloat_data = cursor.fetchall()
            for table, dead_tups in bloat_data:
                self.stdout.write(f"Table: {table} | Dead Tuples: {dead_tups}")
        
        # 4. Operational Events (Permanent - Keep for Audit)
        self.stdout.write("Operational Events (Audit Timeline) preserved for permanent forensic trail.")

        self.stdout.write("SUCCESS: Data retention policy enforced.")
