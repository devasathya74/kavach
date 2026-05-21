from django.utils import timezone
from datetime import timedelta
from django.core.exceptions import PermissionDenied
from .review_models import CommanderReview
from .models import DisciplineScore

class ReviewEngine:
    DAILY_LIMIT = 5
    COOLDOWN_HOURS = 24

    def __init__(self, reviewer):
        self.reviewer = reviewer

    def submit_review(self, target_officer, rating, reason):
        # 1. Gatekeeping Layer (Pre-check)
        if self.reviewer.reviewer_bias_level == 'SUSPICIOUS' and self.reviewer.reviewer_trust_score < 40:
            raise PermissionDenied("Your review privilege is suspended due to low trust score.")

        if len(reason) < 20:
            raise PermissionDenied("Review rejected. Please provide a detailed reason (min 20 chars).")

        # 2. Damage Cap (Reputation Shield: max ±10 per day)
        daily_delta = CommanderReview.objects.filter(
            officer=target_officer,
            status='APPROVED',
            created_at__gt=timezone.now() - timedelta(days=1)
        ).aggregate(models.Sum('delta_score'))['delta_score__sum'] or 0
        
        # 2a. Velocity Control (max ±30 per week)
        weekly_delta = CommanderReview.objects.filter(
            officer=target_officer,
            status='APPROVED',
            created_at__gt=timezone.now() - timedelta(days=7)
        ).aggregate(models.Sum('delta_score'))['delta_score__sum'] or 0
        
        if abs(weekly_delta + (rating * 2)) > 30:
            raise PermissionDenied("Velocity Limit Reached: Too many score changes for this officer this week.")

        # 2b. Momentum Detection (7-day weighted trend)
        history_7 = ds.reputation_history[-7:]
        if len(history_7) >= 3:
            # Calculate momentum: weighted sum of recent deltas
            deltas = [history_7[i]['score'] - history_7[i-1]['score'] for i in range(1, len(history_7))]
            weighted_momentum = sum(d * (i+1) for i, d in enumerate(deltas)) / sum(range(1, len(deltas)+1))
            ds.momentum_score = weighted_momentum
            
            if abs(weighted_momentum) > 5: # High momentum threshold
                raise PermissionDenied("⚠️ Momentum Lock: Persistent directional behavior detected. Audit Required.")

        # 2c. Cross-Reviewer Correlation (Cluster Bias)
        unique_reviewers = CommanderReview.objects.filter(
            officer=target_officer,
            created_at__gt=timezone.now() - timedelta(days=3)
        ).values('reviewer').distinct().count()
        
        if unique_reviewers >= 3:
            ds.cluster_bias_flag = True
            raise PermissionDenied("🚩 Cluster Alert: Multiple reviewers targeting same officer. Cross-check needed.")

        if abs(daily_delta + (rating * 2)) > 10:
            rating = 5 if rating > 0 else -5

        # 3. Deviation Check (High-Impact Gate)
        ds, _ = DisciplineScore.objects.get_or_create(officer=target_officer)
        deviation = abs(rating * 5)
        status = 'APPROVED'
        approvals_needed = 1
        
        if deviation > 20: 
            status = 'PENDING'
            approvals_needed = 2 # 2/3 Rule: System + CO or CC + CO

        # 4. Daily Limit Check
        today_count = CommanderReview.objects.filter(
            reviewer=self.reviewer, 
            created_at__gt=timezone.now() - timedelta(days=1)
        ).count()
        if today_count >= self.DAILY_LIMIT:
            raise PermissionDenied(f"Daily review limit reached ({self.DAILY_LIMIT}).")

        # 4. Cooldown Check
        recent = CommanderReview.objects.filter(
            officer=target_officer, 
            reviewer=self.reviewer,
            created_at__gt=timezone.now() - timedelta(hours=self.COOLDOWN_HOURS)
        ).exists()
        if recent:
            raise PermissionDenied("Cooldown active for this officer. Try again later.")

        # 5. Create Review (Queue if high deviation)
        review = CommanderReview.objects.create(
            officer=target_officer,
            reviewer=self.reviewer,
            rating=max(-10, min(10, rating)),
            reason=reason,
            status=status,
            previous_score=ds.score,
            approvals_needed=approvals_needed,
            approvals_count=1 if status == 'APPROVED' else 0,
            system_validation_pass=(deviation < 30) # AI validation check
        )
        
        if status == 'APPROVED':
            self.recompute_final_score(target_officer, review)
        
        return review

    def recompute_final_score(self, officer, review_obj):
        ds, _ = DisciplineScore.objects.get_or_create(officer=officer)
        
        # 1. Bias Multiplier for the REVIEWER
        from .bias_engine import BiasDetectionEngine
        bias_engine = BiasDetectionEngine(self.reviewer)
        multiplier = bias_engine.analyze_reviewer() # 0.1 to 1.0
        
        # 2. Dynamic Weight
        human_weight = 0.3 * multiplier
        system_weight = 1.0 - human_weight

        # 3. Get average human rating (only approved/valid)
        reviews = CommanderReview.objects.filter(officer=officer, is_valid=True, status='APPROVED')
        avg_rating = sum(r.rating for r in reviews) / reviews.count() if reviews.exists() else 0
        
        # 4. Final Weighted Formula
        final_score = (ds.score * system_weight) + ((ds.score + (avg_rating * 5)) * human_weight)
        
        old_score = ds.score
        ds.score = int(max(0, min(100, final_score)))
        ds.save()

        # Update Forensic Audit on the review object
        review_obj.new_score = ds.score
        review_obj.delta_score = ds.score - old_score
        review_obj.save()
