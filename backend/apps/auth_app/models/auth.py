from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models
from django.utils import timezone
import uuid

# --- MASTER DATA TABLES ---

class RankMaster(models.Model):
    code   = models.CharField(max_length=20, unique=True)
    name   = models.CharField(max_length=100)
    level  = models.IntegerField(help_text="Higher is more senior (e.g., CO=10, Constable=1)")
    active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.name} ({self.code})"

    class Meta:
        db_table = 'rank_master'
        ordering = ['-level']

class UnitMaster(models.Model):
    UNIT_TYPES = [
        ('HQ', 'Headquarters'),
        ('RTC', 'Recruit Training Center'),
        ('BATTALION', 'Battalion / Company Units'),
    ]
    name   = models.CharField(max_length=100)
    code   = models.CharField(max_length=20, unique=True)
    type   = models.CharField(max_length=10, choices=UNIT_TYPES, default='BATTALION')
    active = models.BooleanField(default=True)

    def __str__(self):
        return self.name

    class Meta:
        db_table = 'unit_master'

class CompanyMaster(models.Model):
    unit   = models.ForeignKey(UnitMaster, on_delete=models.CASCADE, related_name='companies')
    name   = models.CharField(max_length=100)
    code   = models.CharField(max_length=20)
    active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.unit.code} - {self.name} Coy"

    class Meta:
        db_table = 'company_master'
        unique_together = ['unit', 'code']

class PlatoonMaster(models.Model):
    company = models.ForeignKey(CompanyMaster, on_delete=models.CASCADE, related_name='platoons')
    name    = models.CharField(max_length=100)
    number  = models.IntegerField()
    active  = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.company.name} - Platoon {self.number}"

    class Meta:
        db_table = 'platoon_master'
        unique_together = ['company', 'number']

# --- MANAGERS ---

class SoftDeleteManager(models.Manager):
    def get_queryset(self):
        return super().get_queryset().filter(deleted_at__isnull=True)

class ActiveOfficerManager(BaseUserManager):
    def get_queryset(self):
        return super().get_queryset().filter(deleted_at__isnull=True, is_active=True)

class OfficerManager(BaseUserManager):
    def create_user(self, pno, role='USER', unit=None, password=None, **extra_fields):
        if not pno:
            raise ValueError("PNO is required")
        
        unit_val = extra_fields.pop('unit_id', unit)
        if not unit_val:
            raise ValueError("Unit is required for tenancy enforcement")
            
        if not isinstance(unit_val, UnitMaster):
             unit_obj = UnitMaster.objects.get(code=unit_val) if isinstance(unit_val, str) else UnitMaster.objects.get(id=unit_val)
        else:
             unit_obj = unit_val

        officer = self.model(pno=pno, role=role, unit=unit_obj, **extra_fields)
        if password:
            officer.set_password(password)
        else:
            officer.set_unusable_password()
        officer.save(using=self._db)
        return officer

    def create_superuser(self, pno, unit, password=None):
        officer = self.create_user(pno, role='ADMIN', unit=unit, password=password)
        officer.is_staff = True
        officer.is_superuser = True
        officer.save(using=self._db)
        return officer

# --- MODELS ---

class Officer(AbstractBaseUser, PermissionsMixin):
    ROLE_CHOICES = [
        ("ADMIN", "Admin"),
        ("PILOT", "Pilot"),
        ("USER", "User"),
    ]
    id   = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    pno  = models.CharField(max_length=9, unique=True, db_index=True)
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='USER')
    unit = models.ForeignKey(UnitMaster, on_delete=models.PROTECT, related_name='officers')
    is_active          = models.BooleanField(default=True)
    is_staff           = models.BooleanField(default=False)
    must_change_password = models.BooleanField(default=True)
    session_version    = models.PositiveIntegerField(default=1)
    deactivated_at     = models.DateTimeField(null=True, blank=True)
    deactivated_by     = models.ForeignKey('self', null=True, blank=True, on_delete=models.SET_NULL, related_name='deactivations_performed')
    deactivated_reason = models.TextField(blank=True)
    revision = models.BigIntegerField(default=1)
    deleted_at = models.DateTimeField(null=True, blank=True)

    @property
    def discipline_score_val(self):
        try:
            return self.discipline_score.score
        except:
            return 100

    @property
    def operational_level_val(self):
        try:
            # We'll use rank level as a proxy for operational level for now
            return f"L{self.profile.rank.level}" if self.profile else "L4"
        except:
            return "L4"
    deleted_by = models.ForeignKey('self', null=True, blank=True, on_delete=models.SET_NULL, related_name='deletions_performed')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    USERNAME_FIELD  = 'pno'
    REQUIRED_FIELDS = ['role', 'unit']
    objects = OfficerManager()
    active_objects = ActiveOfficerManager()
    def delete(self, actor=None, *args, **kwargs):
        self.deleted_at = timezone.now()
        self.deleted_by = actor
        self.is_active = False
        self.save()
    def deactivate(self, actor, reason):
        self.is_active = False
        self.deactivated_at = timezone.now()
        self.deactivated_by = actor
        self.deactivated_reason = reason
        self.save()
    def __str__(self):
        return f"{self.pno} | {self.role} | {self.unit.code if self.unit else 'No Unit'}"
    class Meta:
        db_table = 'officers'

class OfficerCredential(models.Model):
    officer             = models.OneToOneField(Officer, on_delete=models.CASCADE, related_name='credentials')
    password_hash       = models.TextField(blank=True)
    last_password_hash  = models.TextField(null=True, blank=True)
    password_changed_at = models.DateTimeField(null=True, blank=True)
    failed_attempts = models.IntegerField(default=0)
    locked_until    = models.DateTimeField(null=True, blank=True)
    last_login_at   = models.DateTimeField(null=True, blank=True)
    last_otp_at      = models.DateTimeField(null=True, blank=True)
    daily_otp_count  = models.IntegerField(default=0)
    permanent_suspicion = models.IntegerField(default=0)
    security_risk_score = models.FloatField(default=0.0)
    silent_risk_score   = models.FloatField(default=0.0)
    last_request_at     = models.BigIntegerField(default=0)
    device_secret       = models.CharField(max_length=128, blank=True)

    def save(self, *args, **kwargs):
        if not self.device_secret:
            import secrets
            self.device_secret = secrets.token_urlsafe(32)
        super().save(*args, **kwargs)

    def generate_new_secret(self):
        """Authoritatively rotate the secret (Emergency only)"""
        import secrets
        new_secret = secrets.token_urlsafe(32)
        self.device_secret = new_secret
        self.save(update_fields=['device_secret'])
        return new_secret

    class Meta:
        db_table = 'officer_credentials'

class OfficerProfile(models.Model):
    SERVICE_STATUS_CHOICES = [
        ('ACTIVE', 'On Duty'),
        ('LEAVE', 'On Leave'),
        ('SUSPENDED', 'Suspended'),
        ('RETIRED', 'Retired/Departed'),
    ]
    officer = models.OneToOneField(Officer, on_delete=models.CASCADE, related_name='profile')
    name    = models.CharField(max_length=100)
    rank    = models.ForeignKey(RankMaster, on_delete=models.PROTECT)
    unit    = models.ForeignKey(UnitMaster, on_delete=models.PROTECT)
    company = models.ForeignKey(CompanyMaster, on_delete=models.PROTECT, null=True, blank=True)
    platoon = models.ForeignKey(PlatoonMaster, on_delete=models.PROTECT, null=True, blank=True)
    reporting_officer = models.ForeignKey(Officer, null=True, blank=True, on_delete=models.SET_NULL, related_name="subordinates_profile")
    image          = models.ImageField(upload_to='profiles/', null=True, blank=True)
    phone          = models.CharField(max_length=15, blank=True)
    email          = models.EmailField(blank=True, null=True)
    service_status = models.CharField(max_length=20, choices=SERVICE_STATUS_CHOICES, default='ACTIVE')
    revision = models.BigIntegerField(default=1)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    class Meta:
        db_table = 'officer_profiles'

class OfficerDevice(models.Model):
    DEVICE_STATUS_CHOICES = [
        ('ACTIVE', 'Active'),
        ('REVOKED', 'Revoked'),
        ('LOST', 'Lost'),
        ('COMPROMISED', 'Compromised'),
        ('RETIRED', 'Retired'),
    ]
    officer         = models.ForeignKey(Officer, on_delete=models.CASCADE, related_name='devices')
    device_id       = models.CharField(max_length=255, db_index=True)
    device_name     = models.CharField(max_length=100, blank=True)
    manufacturer    = models.CharField(max_length=100, blank=True)
    android_version = models.CharField(max_length=20, blank=True)
    app_version     = models.CharField(max_length=20, blank=True)
    last_ip         = models.GenericIPAddressField(null=True, blank=True)
    status          = models.CharField(max_length=20, choices=DEVICE_STATUS_CHOICES, default='ACTIVE')
    integrity_level = models.CharField(max_length=50, default='UNKNOWN')
    trust_score     = models.FloatField(default=100.0)
    registered_at           = models.DateTimeField(auto_now_add=True)
    last_active             = models.DateTimeField(auto_now=True)
    last_integrity_check_at = models.DateTimeField(null=True, blank=True)
    class Meta:
        db_table = 'officer_devices'
        unique_together = ['officer', 'device_id']

class OtpRecord(models.Model):
    officer    = models.ForeignKey(Officer, on_delete=models.CASCADE)
    otp_hash   = models.CharField(max_length=128)
    device_id  = models.CharField(max_length=255)
    expires_at = models.DateTimeField()
    used       = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    class Meta:
        db_table = 'otp_records'
