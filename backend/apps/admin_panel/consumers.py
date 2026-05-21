import json
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser

class BroadcastConsumer(AsyncWebsocketConsumer):
    async def connect(self):
        self.room_name = "kavach_broadcast"
        self.room_group_name = f"group_{self.room_name}"

        # SECURITY: Reject anonymous connections
        user = self.scope.get('user')
        if not user or user.is_anonymous:
            await self.close(code=4003) # Unauthorized
            return

        # TARGETED GROUPS
        self.user_group_name = f"user_{user.pno}"
        self.unit_group_name = f"unit_{user.unit.code}"

        # Join groups
        await self.channel_layer.group_add(self.room_group_name, self.channel_name)
        await self.channel_layer.group_add(self.user_group_name, self.channel_name)
        await self.channel_layer.group_add(self.unit_group_name, self.channel_name)

        await self.accept()

        # Send current broadcast status if any
        await self.send(text_data=json.dumps({
            'type': 'INFO',
            'message': f'Connected to Kavach Command Channel as {user.pno}'
        }))

    async def disconnect(self, close_code):
        # Leave room groups
        if hasattr(self, 'room_group_name'):
            await self.channel_layer.group_discard(self.room_group_name, self.channel_name)
        if hasattr(self, 'user_group_name'):
            await self.channel_layer.group_discard(self.user_group_name, self.channel_name)
        if hasattr(self, 'unit_group_name'):
            await self.channel_layer.group_discard(self.unit_group_name, self.channel_name)

    # Receive message from WebSocket
    async def receive(self, text_data):
        user = self.scope.get('user')
        if not user or user.is_anonymous:
            return

        data = json.loads(text_data)
        action_type = data.get('type')

        if action_type == 'RAISE_HAND':
            # Notify admins in the group
            user_name = getattr(user.profile, 'name', 'Unknown Officer') if hasattr(user, 'profile') else 'Unknown Officer'
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'broadcast_message',
                    'message': {
                        'type': 'HAND_RAISED',
                        'user_pno': user.pno,
                        'user_name': user_name
                    }
                }
            )

        elif action_type == 'ADMIN_COMMAND':
            # Only allow if user is ADMIN or SUPERUSER
            if self.scope['user'].role in ['ADMIN', 'SUPERUSER']:
                command = data.get('command')
                await self.channel_layer.group_send(
                    self.room_group_name,
                    {
                        'type': 'broadcast_message',
                        'message': {
                            'type': 'COMMAND_EXECUTED',
                            'command': command,
                            'params': data.get('params')
                        }
                    }
                )

    # Receive message from room group
    async def broadcast_message(self, event):
        message = event['message']

        # Send message to WebSocket
        await self.send(text_data=json.dumps(message))
