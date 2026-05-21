import json
import asyncio
import time
import logging
from channels.generic.websocket import AsyncWebsocketConsumer
from django.conf import settings
from kavach_backend.logging_utils import websocket_exception_interceptor
from kavach_backend.metrics import (
    ACTIVE_WEBSOCKETS, 
    WEBSOCKET_CONNECTIONS_TOTAL, 
    WEBSOCKET_MESSAGES_RECEIVED, 
    WEBSOCKET_MESSAGES_SENT
)

logger = logging.getLogger('kavach.websocket')

class BroadcastConsumer(AsyncWebsocketConsumer):
    """
    Highly resilient WebSocket consumer for Kavach Command Channel.
    Enforces timeout budgets, message rate limits, queue size caps,
    and automatic disconnection of slow consumers to prevent event loop starvation.
    """
    @websocket_exception_interceptor
    async def connect(self):
        self.room_name = "kavach_broadcast"
        self.room_group_name = f"group_{self.room_name}"
        self._message_timestamps = []

        # Try increasing active connection metrics
        try:
            ACTIVE_WEBSOCKETS.inc()
        except Exception:
            pass

        # SECURITY: Reject anonymous connections
        user = self.scope.get('user')
        if not user or user.is_anonymous:
            try:
                WEBSOCKET_CONNECTIONS_TOTAL.labels(status="rejected_unauthorized").inc()
            except Exception:
                pass
            await self.close(code=4003) # Unauthorized
            return

        # TARGETED GROUPS
        self.user_group_name = f"user_{user.pno}"
        self.unit_group_name = f"unit_{user.unit.code}" if hasattr(user, 'unit') and user.unit else "unit_none"

        try:
            # Enforce timeout budgets for channel layer additions to prevent blocking event loop indefinitely
            await asyncio.wait_for(self.channel_layer.group_add(self.room_group_name, self.channel_name), timeout=2.0)
            await asyncio.wait_for(self.channel_layer.group_add(self.user_group_name, self.channel_name), timeout=2.0)
            await asyncio.wait_for(self.channel_layer.group_add(self.unit_group_name, self.channel_name), timeout=2.0)
        except asyncio.TimeoutError:
            logger.error(f"Channel group joining timed out for user {user.pno}. Shedding connection.")
            try:
                WEBSOCKET_CONNECTIONS_TOTAL.labels(status="timeout").inc()
            except Exception:
                pass
            await self.close(code=4008) # Slow connection / Timeout
            return
        except asyncio.CancelledError:
            logger.warning(f"Connection setup for user {user.pno} cancelled during channel layer grouping.")
            raise

        await self.accept()
        try:
            WEBSOCKET_CONNECTIONS_TOTAL.labels(status="success").inc()
        except Exception:
            pass

        # Send current broadcast status if any
        await self.safe_send({
            'type': 'INFO',
            'message': f'Connected to Kavach Command Channel as {user.pno}'
        })

    @websocket_exception_interceptor
    async def disconnect(self, close_code):
        try:
            ACTIVE_WEBSOCKETS.dec()
        except Exception:
            pass
            
        # Leave room groups with strict timeout budget
        try:
            if hasattr(self, 'room_group_name'):
                await asyncio.wait_for(self.channel_layer.group_discard(self.room_group_name, self.channel_name), timeout=1.0)
            if hasattr(self, 'user_group_name'):
                await asyncio.wait_for(self.channel_layer.group_discard(self.user_group_name, self.channel_name), timeout=1.0)
            if hasattr(self, 'unit_group_name'):
                await asyncio.wait_for(self.channel_layer.group_discard(self.unit_group_name, self.channel_name), timeout=1.0)
        except asyncio.TimeoutError:
            logger.warning(f"Discarding channels timed out during consumer disconnect hook.")
        except asyncio.CancelledError:
            raise
        except Exception as e:
            logger.error(f"Error while disconnecting WebSocket consumer: {str(e)}")

    @websocket_exception_interceptor
    async def receive(self, text_data):
        user = self.scope.get('user')
        if not user or user.is_anonymous:
            return

        try:
            WEBSOCKET_MESSAGES_RECEIVED.inc()
        except Exception:
            pass

        # ── Backpressure Cap & Message Throttling ──
        now = time.time()
        self._message_timestamps = [t for t in self._message_timestamps if now - t < 1.0]
        if len(self._message_timestamps) >= 5: # Limit to 5 msg/sec
            logger.warning(f"User {user.pno} connection exceeded message frequency ceiling. Disconnecting slow/abusive consumer.")
            await self.safe_send({
                "status": False,
                "error": {"code": "MESSAGE_FLOOD", "message": "Message rate limit of 5 frames/sec exceeded. Connection dropped."}
            })
            await self.close(code=4029) # Shed connection
            return
            
        self._message_timestamps.append(now)

        try:
            data = json.loads(text_data)
        except json.JSONDecodeError:
            await self.safe_send({
                "status": False,
                "error": {"code": "MALFORMED_FRAME", "message": "WebSocket frame is not valid JSON."}
            })
            return
            
        action_type = data.get('type')

        if action_type == 'RAISE_HAND':
            user_name = getattr(user.profile, 'name', 'Unknown Officer') if hasattr(user, 'profile') else 'Unknown Officer'
            try:
                await asyncio.wait_for(
                    self.channel_layer.group_send(
                        self.room_group_name,
                        {
                            'type': 'broadcast_message',
                            'message': {
                                'type': 'HAND_RAISED',
                                'user_pno': user.pno,
                                'user_name': user_name
                            }
                        }
                    ),
                    timeout=2.0
                )
            except asyncio.TimeoutError:
                logger.error(f"Broadcast RAISE_HAND timed out.")
            except asyncio.CancelledError:
                raise

        elif action_type == 'ADMIN_COMMAND':
            if getattr(user, 'role', 'guest') in ['ADMIN', 'SUPERUSER']:
                command = data.get('command')
                try:
                    await asyncio.wait_for(
                        self.channel_layer.group_send(
                            self.room_group_name,
                            {
                                'type': 'broadcast_message',
                                'message': {
                                    'type': 'COMMAND_EXECUTED',
                                    'command': command,
                                    'params': data.get('params')
                                }
                            }
                        ),
                        timeout=2.0
                    )
                except asyncio.TimeoutError:
                    logger.error(f"Broadcast ADMIN_COMMAND timed out.")
                except asyncio.CancelledError:
                    raise

    async def broadcast_message(self, event):
        message = event['message']
        await self.safe_send(message)

    async def safe_send(self, data):
        """
        Transmits a WebSocket payload using a strict timeout budget to avoid
        indefinite blocks on slow connections, shedding slow consumers gracefully.
        """
        try:
            await asyncio.wait_for(self.send(text_data=json.dumps(data)), timeout=1.0)
            try:
                WEBSOCKET_MESSAGES_SENT.inc()
            except Exception:
                pass
        except asyncio.TimeoutError:
            logger.warning(f"Websocket transmission timed out. Gracefully shedding slow consumer connection.")
            await self.close(code=4008) # Shedding slow consumer connection
        except asyncio.CancelledError:
            logger.info("Websocket send task cancelled in flight.")
            raise
        except Exception as e:
            logger.error(f"Failed to transmit websocket message securely: {str(e)}")
