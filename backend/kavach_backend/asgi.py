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
from apps.admin_panel.consumers import BroadcastConsumer


from apps.auth_app.middleware import TokenAuthMiddleware

application = ProtocolTypeRouter({
    "http": django_asgi_app,

    "websocket": TokenAuthMiddleware(
        URLRouter([
            path("api/v2/ws/broadcast/", BroadcastConsumer.as_asgi()),
            path("ws/broadcast/", BroadcastConsumer.as_asgi()),  # Legacy support
            path("ws/live/", BroadcastConsumer.as_asgi()),       # Added for consistency
        ])
    ),
})
