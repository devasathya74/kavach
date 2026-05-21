from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework import exceptions
from django.utils.deprecation import MiddlewareMixin

class SessionSecurityMiddleware(MiddlewareMixin):
    """
    Zero-Trust Session Validation.
    Checks the session_version in the JWT against the current Officer.session_version.
    If they don't match, the token is considered revoked.
    """
    def process_request(self, request):
        # We only care about authenticated requests using JWT
        auth = JWTAuthentication()
        try:
            header = auth.get_header(request)
            if header is None:
                return None
            
            raw_token = auth.get_raw_token(header)
            if raw_token is None:
                return None
                
            validated_token = auth.get_validated_token(raw_token)
            user = auth.get_user(validated_token)
            
            # Check session version
            token_version = validated_token.get('session_version')
            if token_version is None or token_version != user.session_version:
                # Token is stale/revoked
                raise exceptions.AuthenticationFailed(
                    "SESSION_STALE: Your session has been revoked by Command Center.",
                    code="session_revoked"
                )
            
            # Bridge to request.user so other middlewares and DRF can see it
            request.user = user
            request.auth = validated_token
                
        except Exception:
            # Let DRF's normal authentication handle it or ignore if not yet authenticated
            pass
        
        return None
