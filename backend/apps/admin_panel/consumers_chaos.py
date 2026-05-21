import json
import logging
from django.conf import settings
from django.db import OperationalError
from .consumers import BroadcastConsumer
from kavach_backend.logging_utils import websocket_exception_interceptor

logger = logging.getLogger('kavach.websocket.chaos')

class ChaosBroadcastConsumer(BroadcastConsumer):
    """
    Staging-only environment gated chaos broadcast consumer.
    Inherits all production behavior but allows simulating outages and performance faults.
    """
    @websocket_exception_interceptor
    async def receive(self, text_data):
        # Enforce sandbox checks
        if not (settings.DEBUG and getattr(settings, 'KAVACH_CHAOS_MODE', False)):
            logger.critical("SECURITY ALERT: Attempted access to Chaos Broadcast Consumer in a Non-Chaos Staging/Prod environment!")
            await self.close(code=4003)
            return

        try:
            data = json.loads(text_data)
        except Exception:
            await super().receive(text_data)
            return

        action_type = data.get('type')

        # Sandbox-isolated chaos hooks
        if action_type == 'CHAOS_SIMULATE_REDIS_OUTAGE':
            logger.warning("CHAOS INSTIGATED: Simulating Redis Outage...")
            raise ConnectionError("Simulated Redis pub/sub channel layer timeout")
            
        elif action_type == 'CHAOS_SIMULATE_DB_DISCONNECT':
            logger.warning("CHAOS INSTIGATED: Simulating PostgreSQL DB Disconnect...")
            raise OperationalError("Simulated PostgreSQL connection drop")
            
        else:
            await super().receive(text_data)
