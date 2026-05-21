from .review_models import CommanderReview
from .models import DisciplineScore

class BiasDetectionEngine:
    DEVIATION_THRESHOLD = 30 # Points

    def __init__(self, reviewer):
        self.reviewer = reviewer

    def analyze_reviewer(self):
        """
        Analyze the reviewer's history for favoritism or excessive negativity.
        """
        reviews = CommanderReview.objects.filter(reviewer=self.reviewer, is_valid=True)
        if reviews.count() < 10:
            return 1.0 # Not enough data for bias detection

        # 1. Deviation Check (AI vs Human)
        deviations = []
        for r in reviews:
            ds = DisciplineScore.objects.filter(officer=r.officer).first()
            if ds:
                # System Score vs Normalized Review Score (rating * 5)
                # This is a bit simplified for now
                deviation = abs(ds.score - (ds.score + r.rating * 5))
                deviations.append(deviation)
        
        avg_deviation = sum(deviations) / len(deviations) if deviations else 0
        
        # 2. Pattern Detection (Target Concentration)
        # Is the reviewer repeatedly boosting/flagging the same small group?
        target_counts = reviews.values('officer').annotate(count=models.Count('id'))
        is_concentrated = any(t['count'] > (reviews.count() * 0.4) for t in target_counts)
        
        # 3. Trust Score Update
        trust = 100.0
        if avg_deviation > self.DEVIATION_THRESHOLD:
            trust -= 30.0
        if is_concentrated:
            trust -= 20.0
            
        self.reviewer.reviewer_trust_score = trust
        if trust < 50:
            self.reviewer.reviewer_bias_level = 'SUSPICIOUS'
        elif trust > 80:
            self.reviewer.reviewer_bias_level = 'HIGH_TRUST'
        else:
            self.reviewer.reviewer_bias_level = 'NORMAL'
        
        self.reviewer.save()
        return trust / 100.0 # Return the multiplier for weight
