from django.core.management.base import BaseCommand
from apps.monitoring.utils import evaluate_rules
import time

class Command(BaseCommand):
    help = 'Runs the KAVACH Alert Evaluation Engine'

    def add_arguments(self, parser):
        parser.add_argument('--loop', action='store_true', help='Run in a continuous loop')

    def handle(self, *args, **options):
        self.stdout.write(self.style.SUCCESS('Starting Alert Engine...'))
        
        if options['loop']:
            while True:
                alerts = evaluate_rules()
                if alerts:
                    self.stdout.write(self.style.WARNING(f"Triggered {len(alerts)} alerts"))
                time.sleep(30)
        else:
            alerts = evaluate_rules()
            self.stdout.write(self.style.SUCCESS(f"Evaluation complete. {len(alerts)} alerts triggered."))
