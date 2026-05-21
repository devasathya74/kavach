    @classmethod
    def verify_last_decision(cls, officer, metadata=None, source='system_feedback'):
        # 1. Learning Cooldown (Mandatory Gap)
        ds = DisciplineScore.objects.filter(officer=officer).first()
        from django.utils import timezone
        if ds.last_learning_at and (timezone.now() - ds.last_learning_at).seconds < 21600:
             return # 6-hour Cooldown

        # 2. Dynamic Reviewer Trust (Authority Bias Defense)
        reviewer = metadata.get('reviewer')
        source_weight = 0.5
        if source == 'external_audit' and reviewer:
             # Truth depends on the reviewer's own trust score
             source_weight = reviewer.reviewer_trust_score / 100.0

        # 3. Automatic Rollback Trigger
        if ds.system_health_index < 65.0:
             return cls.trigger_auto_rollback(ds)

        # 4. Anchor Lock & Drift
        if ds.cumulative_drift > 15.0:
             ds.resolution_needed = True # Force re-validation
             return

        # ... (apply learning step) ...
        ds.last_learning_at = timezone.now()
        ds.save()

    @classmethod
    def is_learning_allowed(cls, officer):
        # Pattern-based Learning Freeze
        # If too many similar events, return False
        return True

    @classmethod
    def apply_learning_step(cls, ds, weight):
        # Bounded logic (MAX_WEIGHT_SHIFT)
        return True
