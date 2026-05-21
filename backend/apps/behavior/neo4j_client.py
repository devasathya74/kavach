from neo4j import GraphDatabase
from django.conf import settings
from decouple import config

class Neo4jClient:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(Neo4jClient, cls).__new__(cls)
            uri = config('NEO4J_URI', default='bolt://localhost:7687')
            user = config('NEO4J_USER', default='neo4j')
            password = config('NEO4J_PASSWORD', default='password')
            cls._instance.driver = GraphDatabase.driver(uri, auth=(user, password))
        return cls._instance

    def close(self):
        self.driver.close()

    def sync_user(self, pno, name=""):
        query = "MERGE (u:User {pno: $pno}) SET u.name = $name"
        with self.driver.session() as session:
            session.run(query, pno=pno, name=name)

    def sync_similarity_edge(self, pno_a, pno_b, score):
        query = """
        MATCH (a:User {pno: $a}), (b:User {pno: $b})
        MERGE (a)-[r:SIMILAR_BEHAVIOR]->(b)
        SET r.score = $score, r.updated_at = datetime()
        """
        with self.driver.session() as session:
            session.run(query, a=pno_a, b=pno_b, score=score)

    def find_clusters(self, threshold=0.8):
        query = """
        MATCH (u:User)-[r:SIMILAR_BEHAVIOR]-(v:User)
        WHERE r.score >= $threshold
        WITH u, count(v) as degree
        WHERE degree >= 3
        RETURN u.pno as pno, degree
        """
        with self.driver.session() as session:
            result = session.run(query, threshold=threshold)
            return [record.data() for record in result]

    def get_pagerank_scores(self):
        # Note: Requires Graph Data Science (GDS) plugin in Neo4j
        query = """
        CALL gds.pageRank.stream('kavach_graph')
        YIELD nodeId, score
        RETURN gds.util.asNode(nodeId).pno AS pno, score
        """
        with self.driver.session() as session:
            try:
                result = session.run(query)
                return {record['pno']: record['score'] for record in result}
            except:
                return {}

    def get_degree_centrality(self):
        query = """
        MATCH (u:User)-[:SIMILAR_BEHAVIOR]-(v)
        RETURN u.pno as pno, count(v) as degree
        """
        with self.driver.session() as session:
            result = session.run(query)
            return {record['pno']: record['degree'] for record in result}

    def get_cascade_stats(self, pno_leader):
        """
        Find followers whose behavior follows the leader's behavior temporally.
        """
        query = """
        MATCH (l:User {pno: $pno})-[:SIMILAR_BEHAVIOR]->(f:User)
        MATCH (l)-[r1:SIMILAR_BEHAVIOR]-(v1), (f)-[r2:SIMILAR_BEHAVIOR]-(v2)
        WHERE r2.updated_at > r1.updated_at
        RETURN count(DISTINCT f) as influenced_count
        """
        with self.driver.session() as session:
            result = session.run(query, pno=pno_leader)
            record = result.single()
            return record['influenced_count'] if record else 0
