from rest_framework import permissions

class IsAdminRole(permissions.BasePermission):
    """
    Allows access only to users with role='ADMIN'.
    """
    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and (request.user.role == 'ADMIN' or request.user.is_superuser))

class IsCommandRole(permissions.BasePermission):
    """
    Allows access to both ADMIN and PILOT roles via centralized logic.
    """
    def has_permission(self, request, view):
        from .security.roles import can_access_personnel
        return can_access_personnel(request.user)

class IsPilotRole(permissions.BasePermission):
    """
    Allows access only to users with role='PILOT'.
    """
    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.role == 'PILOT')
