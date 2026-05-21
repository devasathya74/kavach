from django.db import models
from django.conf import settings

class BehavioralBaseline(models.Model):
    officer = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='fingerprint_baseline')
    
    # Interaction Timing
    avg_read_speed = models.FloatField(default=0) # chars per second
    avg_ack_delay = models.FloatField(default=0)  # seconds
    
    # Routine
    usual_active_hours = models.JSONField(default=list) # e.g. [9, 10, 11, 20, 21]
    avg_session_duration = models.FloatField(default=0) # seconds
    
    # Device/Network
    common_ips = models.JSONField(default=list)
    common_locations = models.JSONField(default=list) # [ {lat, lng, radius} ]
    
    # Metadata
    last_updated = models.DateTimeField(auto_now=True)
    is_mature = models.BooleanField(default=False) # True if 7+ days of data

    class Meta:
        db_table = 'behavioral_baselines'

class FingerprintDeviation(models.Model):
    officer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='deviations')
    score = models.IntegerField() # 0-100
    reasons = models.JSONField(default=list)
    detected_at = models.DateTimeField(auto_now_add=True)
    
    # Analysis Details
    details = models.JSONField(default=dict)

    class Meta:
        db_table = 'fingerprint_deviations'
        ordering = ['-detected_at']

class BehavioralAnalyzer:
    def __init__(self, officer):
        self.officer = officer
        self.baseline, _ = BehavioralBaseline.objects.get_or_create(officer=officer)

    def analyze_session(self, session_data):
        """
        Compare current session data with baseline.
        session_data: { read_speed, ack_delay, hour, location, ip }
        """
        score = 0
        reasons = []

        if not self.baseline.is_mature:
            return 0, ["Baseline still maturing"]

        # 1. Interaction Timing Deviation
        if self.baseline.avg_read_speed > 0:
            diff = abs(session_data.get('read_speed', 0) - self.baseline.avg_read_speed)
            if diff > self.baseline.avg_read_speed * 0.5:
                score += 30
                reasons.append("Abnormal interaction speed")

        # 2. Routine Hour Deviation
        current_hour = session_data.get('hour')
        if current_hour is not None and current_hour not in self.baseline.usual_active_hours:
            score += 20
            reasons.append(f"Activity outside usual hours ({current_hour}:00)")

        # 3. Location Deviation
        # (Assuming location check logic here)
        
        # Save Deviation Log
        if score > 30:
            FingerprintDeviation.objects.create(
                officer=self.officer,
                score=score,
                reasons=reasons,
                details=session_data
            )
        
        return score, reasons

    def update_baseline(self, new_data):
        # Update logic to refine baseline over 7 days
        # This would involve averaging existing and new values
        pass
