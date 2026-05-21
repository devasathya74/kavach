import os
from django.core.asgi import get_asgi_application
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.auth import AuthMiddlewareStack
from django.urls import path

import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

# Initialize Django ASGI application early to ensure apps are loaded
django_asgi_app = get_asgi_application()

# Import consumers after django.setup()
from django.conf import settings
from apps.auth_app.middleware import TokenAuthMiddleware
from kavach_backend.asgi_middleware import KavachASGIExceptionMiddleware

if settings.DEBUG and getattr(settings, 'KAVACH_CHAOS_MODE', False):
    from apps.admin_panel.consumers_chaos import ChaosBroadcastConsumer as ActiveBroadcastConsumer
else:
    from apps.admin_panel.consumers import BroadcastConsumer as ActiveBroadcastConsumer

application = KavachASGIExceptionMiddleware(
    ProtocolTypeRouter({
        "http": django_asgi_app,

        "websocket": TokenAuthMiddleware(
            URLRouter([
                path("api/v2/ws/broadcast/", ActiveBroadcastConsumer.as_asgi()),
                path("ws/broadcast/", ActiveBroadcastConsumer.as_asgi()),  # Legacy support
                path("ws/live/", ActiveBroadcastConsumer.as_asgi()),       # Added for consistency
            ])
        ),
    })
)
