from rest_framework_simplejwt.tokens import RefreshToken
from ..models import Officer, OfficerCredential, OfficerActivity
from ..security.roles import can_manage
from django.utils import timezone

class AuthService:
    
    @staticmethod
    def login(pno, password, device_id, client_ip=None):
        import logging
        from django.core.cache import cache
        
        logger = logging.getLogger('kavach.auth')
        sec_logger = logging.getLogger('kavach.security')
        
        # Helper to extract subnet
        def get_subnet(ip):
            if not ip:
                return 'unknown'
            if ':' in ip:  # IPv6
                parts = ip.split(':')
                return ':'.join(parts[:3])
            else:  # IPv4
                parts = ip.split('.')
                return '.'.join(parts[:3])
                
        subnet = get_subnet(client_ip)
        device_lock_key = f"lock_user_{pno}_dev_{device_id}"
        ip_lock_key = f"lock_ip_{client_ip}"
        subnet_lock_key = f"lock_subnet_{subnet}"
        
        # 1. Enforce Multi-Vector Lockouts
        if cache.get(device_lock_key):
            sec_logger.warning(f"Login blocked for {pno} on device {device_id}: Device Lock active.")
            return None, "Authentication temporarily suspended due to security threshold lockouts."
            
        if client_ip and cache.get(ip_lock_key):
            sec_logger.warning(f"Login blocked from IP {client_ip}: IP Lock active.")
            return None, "Authentication temporarily suspended due to security threshold lockouts."
            
        if client_ip and cache.get(subnet_lock_key):
            sec_logger.warning(f"Login blocked from Subnet {subnet}: Subnet Lock active.")
            return None, "Authentication temporarily suspended due to regional security restrictions."

        try:
            officer = Officer.objects.get(pno=pno)
            
            # Check password
            if not officer.check_password(password):
                # ── AUTHENTICATION FAILURE - TRACK & LOCK ──
                user_fails = cache.get(f"fails_user_{pno}", 0) + 1
                cache.set(f"fails_user_{pno}", user_fails, timeout=900)
                
                delay = 0
                if user_fails >= 5:
                    lock_occ = cache.get(f"locks_count_{pno}", 0) + 1
                    cache.set(f"locks_count_{pno}", lock_occ, timeout=86400) # 24h reset
                    
                    # Exponential backoff: 1 min, 4 mins, 16 mins, 60 mins max
                    delay = min(3600, (4 ** (lock_occ - 1)) * 60)
                    cache.set(device_lock_key, True, timeout=delay)
                    sec_logger.warning(f"Device lock applied to {pno} on {device_id} for {delay}s after {user_fails} failures.")
                    
                if client_ip:
                    ip_fails = cache.get(f"fails_ip_{client_ip}", 0) + 1
                    cache.set(f"fails_ip_{client_ip}", ip_fails, timeout=900)
                    
                    if ip_fails >= 20:
                        if not delay:
                            delay = 300 # Default 5 mins for IP lock
                        cache.set(ip_lock_key, True, timeout=delay)
                        sec_logger.critical(f"IP Lock applied to {client_ip} for {delay}s after {ip_fails} failures.")
                        
                    subnet_fails = cache.get(f"fails_subnet_{subnet}", 0) + 1
                    cache.set(f"fails_subnet_{subnet}", subnet_fails, timeout=900)
                    
                    if subnet_fails >= 50:
                        if not delay:
                            delay = 900 # Default 15 mins for Subnet lock
                        cache.set(subnet_lock_key, True, timeout=delay)
                        sec_logger.critical(f"SUBNET Lock applied to {subnet} for {delay}s after {subnet_fails} failures.")
                
                return None, "Invalid password"
            
            if not officer.is_active:
                return None, "Account suspended"
                
            # Success! Clear failure counters for this user and IP
            cache.delete(f"fails_user_{pno}")
            if client_ip:
                cache.delete(f"fails_ip_{client_ip}")
                # We don't delete subnet fails to prevent distributed brute-force resetting of subnets
            
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
            # Track failures even for non-existent users to prevent user enumeration / DB CPU starvation
            if client_ip:
                ip_fails = cache.get(f"fails_ip_{client_ip}", 0) + 1
                cache.set(f"fails_ip_{client_ip}", ip_fails, timeout=900)
                if ip_fails >= 20:
                    cache.set(ip_lock_key, True, timeout=300)
                    
                subnet_fails = cache.get(f"fails_subnet_{subnet}", 0) + 1
                cache.set(f"fails_subnet_{subnet}", subnet_fails, timeout=900)
                if subnet_fails >= 50:
                    cache.set(subnet_lock_key, True, timeout=900)
                    
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
