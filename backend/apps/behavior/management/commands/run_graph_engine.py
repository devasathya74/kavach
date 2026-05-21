from django.core.management.base import BaseCommand
from apps.behavior.graph_engine import GraphIntelligenceEngine

class Command(BaseCommand):
    help = 'Runs KAVACH Graph Intelligence Engine to detect behavior clusters'

    def handle(self, *args, **options):
        self.stdout.write("Scanning behavior network...")
        engine = GraphIntelligenceEngine()
        
        engine.rebuild_graph()
        num_clusters = engine.detect_clusters()
        
        self.stdout.write(self.style.SUCCESS(f"Graph Intelligence: {num_clusters} suspicious clusters detected."))
