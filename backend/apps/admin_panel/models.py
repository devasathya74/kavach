from django.db import models
from django.conf import settings
import hashlib

class AppRelease(models.Model):
    CHANNELS = [
        ('PILOT', 'Pilot (Internal/Unstable)'),
        ('STABLE', 'Stable (Officers/Production)'),
        ('EMERGENCY', 'Emergency (Hotfix)'),
    ]
    
    version_code = models.IntegerField(unique=True)
    version_name = models.CharField(max_length=50)
    apk_file = models.FileField(upload_to='builds/')
    apk_hash_sha256 = models.CharField(max_length=64, help_text="SHA-256 hash of the APK for integrity verification.")
    channel = models.CharField(max_length=20, choices=CHANNELS, default='PILOT')
    
    # Update logic
    is_forced = models.BooleanField(default=False, help_text="If true, user cannot dismiss the update prompt.")
    min_supported_version = models.IntegerField(default=1, help_text="Versions below this will be forced to update.")
    release_notes = models.TextField(blank=True)
    
    # Rollout Control
    rollout_percentage = models.IntegerField(default=100, help_text="Percentage of users (0-100) who will receive this update.")
    is_critical_override = models.BooleanField(default=False, help_text="If true, staged rollout percentage is ignored.")
    expires_at = models.DateTimeField(null=True, blank=True, help_text="Optional expiry date (useful for Emergency hotfixes).")
    
    # Versioning metadata
    schema_version = models.IntegerField(default=1, help_text="Current schema version of this release.")
    min_supported_schema = models.IntegerField(default=1, help_text="Lowest schema version this app version can handle (for rollback safety).")
    
    # Rollback support
    is_rollback_allowed = models.BooleanField(default=False)
    previous_release = models.ForeignKey('self', on_delete=models.SET_NULL, null=True, blank=True, related_name='next_releases')
    
    # Approval chain (Hardened)
    is_approved = models.BooleanField(default=False)
    approval_reason = models.TextField(blank=True, help_text="Mandatory for Critical/Emergency releases.")
    
    # Structured Governance
    CATEGORIES = [
        ('SECURITY', 'Security Patch'),
        ('HOTFIX', 'Bug Hotfix'),
        ('ROLLBACK', 'Release Rollback'),
        ('PLANNED', 'Planned Feature Update'),
    ]
    release_category = models.CharField(max_length=20, choices=CATEGORIES, default='PLANNED')
    incident_id = models.CharField(max_length=50, blank=True, help_text="Link to internal incident tracker.")
    blast_radius = models.CharField(max_length=10, choices=[('LOW', 'Low'), ('MEDIUM', 'Medium'), ('HIGH', 'High')], default='LOW')
    
    approved_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name='approved_releases')
    approved_at = models.DateTimeField(null=True, blank=True)
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'app_releases'
        ordering = ['-version_code']

    def __str__(self):
        return f"v{self.version_code} ({self.channel}) - Approved: {self.is_approved}"

    def save(self, *args, **kwargs):
        import uuid
        import os
        
        # Isolation: Randomize filename on initial save to prevent direct guessing
        if self.apk_file and not self.pk:
            ext = os.path.splitext(self.apk_file.name)[1]
            self.apk_file.name = f"{uuid.uuid4().hex}{ext}"

        # Auto-calculate hash if file is present and hash is missing
        if self.apk_file and not self.apk_hash_sha256:
            hasher = hashlib.sha256()
            for chunk in self.apk_file.chunks():
                hasher.update(chunk)
            self.apk_hash_sha256 = hasher.hexdigest()
        super().save(*args, **kwargs)
