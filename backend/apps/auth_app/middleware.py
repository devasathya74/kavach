from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser
from apps.auth_app.models import Officer
import jwt
from django.conf import settings
from urllib.parse import parse_qs

@database_sync_to_async
def get_user(token):
    try:
        # Assuming simplejwt is used for token generation
        from rest_framework_simplejwt.tokens import AccessToken
        access_token = AccessToken(token)
        user_id = access_token['user_id']
        return Officer.objects.get(id=user_id)
    except Exception:
        return AnonymousUser()

class TokenAuthMiddleware:
    def __init__(self, inner):
        self.inner = inner

    async def __call__(self, scope, receive, send):
        query_string = parse_qs(scope['query_string'].decode())
        token = query_string.get('token', [None])[0]
        
        if token:
            scope['user'] = await get_user(token)
        else:
            scope['user'] = AnonymousUser()
            
        return await self.inner(scope, receive, send)
