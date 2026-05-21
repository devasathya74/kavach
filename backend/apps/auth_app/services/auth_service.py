from rest_framework_simplejwt.tokens import RefreshToken
from ..models import Officer, OfficerCredential, OfficerActivity
from ..security.roles import can_manage
from django.utils import timezone

class AuthService:
    
    @staticmethod
    def login(pno, password, device_id):
        try:
            officer = Officer.objects.get(pno=pno)
            if not officer.check_password(password):
                return None, "Invalid password"
            
            if not officer.is_active:
                return None, "Account suspended"
                
            refresh = RefreshToken.for_user(officer)
            
            # Ensure credentials and device secret
            try:
                creds = officer.credentials
            except OfficerCredential.DoesNotExist:
                creds = OfficerCredential.objects.create(officer=officer)
                
            if not creds.device_secret:
                creds.generate_new_secret()
                
            # Audit
            AuthService._audit(officer, 'LOGIN_SUCCESS', device_id)
            
            return {
                'token': str(refresh.access_token),
                'refresh_token': str(refresh),
                'user': officer,
                'creds': creds
            }, None
            
        except Officer.DoesNotExist:
            return None, "Account not found"

    @staticmethod
    def change_password(officer, old_password, new_password, device_id):
        if not officer.check_password(old_password):
            return False, "Incorrect old password"
            
        if len(new_password) < 8:
            return False, "Password too short"
            
        officer.set_password(new_password)
        officer.must_change_password = False
        officer.save()
        
        # Update credentials history
        cred = officer.credentials
        cred.last_password_hash = cred.password_hash
        cred.password_hash = officer.password
        cred.password_changed_at = timezone.now()
        cred.save()
        
        AuthService._audit(officer, 'CHANGE_PASSWORD_SUCCESS', device_id)
        return True, "Password updated successfully"

    @staticmethod
    def _audit(officer, action, device_id, severity='INFO'):
        OfficerActivity.objects.create(
            officer=officer,
            pno=officer.pno,
            unit=officer.unit,
            action=action,
            severity=severity,
            result='SUCCESS'
        )
