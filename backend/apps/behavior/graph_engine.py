import numpy as np
from .models import DisciplineScore, BehaviorEvent
from .graph_models import SimilarityEdge, BehavioralCluster, ClusterMembership
from django.db.models import Q
from django.utils import timezone
from datetime import timedelta

class GraphIntelligenceEngine:
    def __init__(self):
        pass

    def compute_similarity(self, u1_score, u2_score):
        """
        Calculate behavioral similarity between two users based on their scores and patterns.
        """
        score = 0
        features = []

        # 1. Anomaly Score Similarity
        if abs(u1_score.anomaly_score - u2_score.anomaly_score) < 10:
            score += 0.3
            features.append("SIMILAR_ANOMALY_MAGNITUDE")

        # 2. Risk Level Match
        if u1_score.risk_level == u2_score.risk_level and u1_score.risk_level != 'NORMAL':
            score += 0.3
            features.append("MATCHING_RISK_PROFILE")

        # 3. Reason Overlap
        u1_reasons = set(u1_score.anomaly_reasons)
        u2_reasons = set(u2_score.anomaly_reasons)
        overlap = u1_reasons.intersection(u2_reasons)
        if overlap:
            score += 0.4
            features.append("SHARED_ANOMALY_REASONS")

        return score, features

from .neo4j_client import Neo4jClient

    def rebuild_graph(self):
        scores = DisciplineScore.objects.filter(anomaly_score__gt=0)
        client = Neo4jClient()
        
        for i, s1 in enumerate(scores):
            client.sync_user(s1.officer.pno, s1.officer.name)
            for s2 in scores[i+1:]:
                sim_score, features = self.compute_similarity(s1, s2)
                
                if sim_score > 0.6:
                    SimilarityEdge.objects.update_or_create(
                        user_a=s1.officer, user_b=s2.officer,
                        defaults={'score': sim_score, 'common_features': features}
                    )
                    # SYNC TO Neo4j
                    client.sync_similarity_edge(s1.officer.pno, s2.officer.pno, sim_score)

    def detect_clusters(self):
        """
        Use Neo4j to find clusters if available, else fallback to internal logic.
        """
        client = Neo4jClient()
        try:
            raw_clusters = client.find_clusters(threshold=0.8)
            # Process raw results and update Django clusters
            # ...
            return len(raw_clusters)
        except Exception as e:
            # Fallback to simple connected components logic
            return self._detect_clusters_fallback()
        
        # Build adjacency list
        adj = {}
        for e in edges:
            adj.setdefault(e.user_a_id, []).append(e.user_b_id)
            adj.setdefault(e.user_b_id, []).append(e.user_a_id)

        visited = set()
        clusters = []

        for user_id in adj:
            if user_id not in visited:
                # New cluster found
                stack = [user_id]
                current_cluster = []
                while stack:
                    curr = stack.pop()
                    if curr not in visited:
                        visited.add(curr)
                        current_cluster.append(curr)
                        stack.extend(adj.get(curr, []))
                
                if len(current_cluster) >= 3:
                    clusters.append(current_cluster)

        # Save clusters to DB
        for i, user_ids in enumerate(clusters):
            cluster_obj = BehavioralCluster.objects.create(
                name=f"Cluster_{timezone.now().strftime('%m%d')}_{i}",
                risk_level='SUSPICIOUS'
            )
            for uid in user_ids:
                ClusterMembership.objects.create(cluster=cluster_obj, officer_id=uid)
        
        return len(clusters)

    def detect_leaders(self):
        """
        Identify influencers and command nodes using hybrid scoring.
        """
        client = Neo4jClient()
        pagerank = client.get_pagerank_scores()
        degree = client.get_degree_centrality()
        
        # Max degree for normalization (avoid division by zero)
        max_deg = max(degree.values()) if degree else 1
        
        for pno, pr_score in pagerank.items():
            deg_score = degree.get(pno, 0) / max_deg
            
            # Hybrid Influence Score
            influence = (0.6 * pr_score) + (0.4 * deg_score)
            
            # Update DisciplineScore
            from apps.auth_app.models import User
            try:
                user = User.objects.get(pno=pno)
                ds, _ = DisciplineScore.objects.get_or_create(officer=user)
                ds.influence_score = influence
                ds.is_command_node = (influence > 0.8)
                if ds.is_command_node:
                    ds.anomaly_reasons.append(f"Leader Detection: High Influence Node (Score: {influence:.2f})")
                ds.save()
            except User.DoesNotExist:
                continue
        
        return len([p for p, i in pagerank.items() if i > 0.7])

    def detect_command_chain_breaks(self):
        """
        Identify when a leader triggers a negative behavioral cascade across the network.
        """
        client = Neo4jClient()
        leaders = DisciplineScore.objects.filter(is_command_node=True)
        from .chain_break_models import ChainBreakAlert
        
        alerts_found = 0
        for ds in leaders:
            leader_pno = ds.officer.pno
            # 1. Get Cascade Stats (followers acting after leader)
            influenced_count = client.get_cascade_stats(leader_pno)
            
            # 2. Compute Cascade Score
            # follower_sync_rate (ratio of influenced followers)
            total_followers = SimilarityEdge.objects.filter(Q(user_a=ds.officer) | Q(user_b=ds.officer)).count()
            sync_rate = influenced_count / total_followers if total_followers > 0 else 0
            
            # temporal_alignment (dummy for now, based on sync rate)
            temporal = sync_rate * 0.9 
            
            cascade_score = (sync_rate * 0.4) + (temporal * 0.4) + (ds.anomaly_score/100 * 0.2)
            
            if cascade_score > 0.7:
                ChainBreakAlert.objects.create(
                    leader=ds.officer,
                    followers_count=influenced_count,
                    cascade_score=cascade_score,
                    pattern_desc=f"Leader triggered {influenced_count} followers with {ds.risk_level} pattern."
                )
                alerts_found += 1
                
        return alerts_found
