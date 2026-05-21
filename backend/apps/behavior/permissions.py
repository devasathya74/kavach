from rest_framework import permissions
from rest_framework.exceptions import PermissionDenied

class ControlledAccessPermission(permissions.BasePermission):
    """
    Implements 'Soft Enforcement': Allows critical data (Orders, Notifications)
    but restricts high-privilege features (Live, Multi-device, Skip).
    """
    def has_permission(self, request, view):
        user = request.user
        if not user or not user.is_authenticated:
            return False
            
        # Command Override or Staff Bypass
        if user.is_staff or user.rank in ['CO', 'DC', 'AC']:
            return True

        try:
            ds = user.discipline_score
            if ds.override_restriction:
                return True

            if ds.trust_level == 'RESTRICTED':
                # SENSITIVE/HIGH-PRIVILEGE VIEWS to block
                restricted_features = ['LiveBroadcastJoinView', 'DeviceRegisterView']
                if view.__class__.__name__ in restricted_features:
                    raise PermissionDenied("This high-privilege feature is currently locked. Maintain discipline to regain access.")
                
                # Allow others (Orders, Notifications, Score)
                return True
        except:
            pass

        return True
