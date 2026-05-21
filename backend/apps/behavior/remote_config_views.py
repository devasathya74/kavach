import hmac
import hashlib
import json
from django.conf import settings
from rest_framework.views import APIView
from rest_framework.response import Response
from .models import RemoteConfig

class RemoteConfigView(APIView):
    """
    Provides signed dynamic configuration to the Android app.
    Prevents configuration tampering through HMAC verification.
    """
    def get(self, request):
        configs = RemoteConfig.objects.all()
        data = {c.key: c.value for c in configs}
        
        if not data:
            data = {
                "VIDEO_COMPLETION_THRESHOLD": 0.6,
                "SESSION_TIMEOUT_MINUTES": 30,
                "MONITORING_MODE": "SAFE",
                "BLOCK_ROOTED": False
            }
        
        # HMAC Signing
        data_str = json.dumps(data, sort_keys=True)
        signature = hmac.new(
            settings.SECRET_KEY.encode(),
            data_str.encode(),
            hashlib.sha256
        ).hexdigest()
        
        return Response({
            "config": data,
            "signature": signature
        })
