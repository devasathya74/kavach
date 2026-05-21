import requests
from django.conf import settings
from django.utils import timezone
from datetime import timedelta
from .models import NotificationLog

class PushNotificationService:
    @staticmethod
    def send_push(user, title, body, priority="NORMAL", screen=None):
        """
        Sends a smart push notification with anti-spam cooldown and priority handling.
        """
        if not user.fcm_token:
            return False

        # 1. Critical Alert Rate Limit (Max 3 per hour)
        if priority == "CRITICAL":
            critical_count = NotificationLog.objects.filter(
                officer=user,
                priority="CRITICAL",
                created_at__gt=timezone.now() - timedelta(hours=1)
            ).count()
            if critical_count >= 3:
                print(f"[PUSH BLOCK] Critical limit reached for {user.pno}")
                return False

        # 2. Anti-Spam Cooldown (60 seconds for same user)
        elif priority != "CRITICAL":
            last_notif = NotificationLog.objects.filter(
                officer=user, 
                created_at__gt=timezone.now() - timedelta(seconds=60)
            ).exists()
            if last_notif:
                print(f"[PUSH SKIP] Cooldown active for {user.pno}")
                return False

        # 2. Log Delivery attempt
        log = NotificationLog.objects.create(
            officer=user,
            pno=user.pno,
            title=title,
            priority=priority
        )

        # 3. Construct FCM payload
        # Using Legacy FCM API for simplicity here, recommended to use V1 in full prod
        url = "https://fcm.googleapis.com/fcm/send"
        headers = {
            "Authorization": f"key={settings.FCM_SERVER_KEY}",
            "Content-Type": "application/json"
        }
        
        data = {
            "to": user.fcm_token,
            "notification": {
                "title": title,
                "body": body,
                "android_channel_id": "kavach_alerts" if priority == "CRITICAL" else "kavach_general"
            },
            "data": {
                "notif_id": log.id,
                "priority": priority,
                "screen": screen, # e.g. "orders", "live"
                "timestamp": str(timezone.now())
            },
            "priority": "high" if priority in ["CRITICAL", "HIGH"] else "normal"
        }

        try:
            response = requests.post(url, json=data, headers=headers, timeout=5)
            if response.status_code == 200:
                log.delivered = True
                log.save(update_fields=['delivered'])
                return True
        except Exception as e:
            print(f"[PUSH ERROR] Failed for {user.pno}: {e}")
        
        return False

from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

class StandardPagination(PageNumberPagination):
    """
    Standardizes Kavach Response Format.
    Ensures 'results' wrapper is always present.
    Prevents 404 on page-out-of-bounds to stop infinite frontend loops.
    """
    page_size = 20
    page_size_query_param = 'page_size'
    max_page_size = 100

    def get_paginated_response(self, data):
        return Response({
            'count': self.page.paginator.count,
            'next': self.get_next_link(),
            'previous': self.get_previous_link(),
            'results': data
        })

    def paginate_queryset(self, queryset, request, view=None):
        try:
            return super().paginate_queryset(queryset, request, view)
        except Exception:
            # If page doesn't exist, return empty list instead of 404
            # This prevents frontend infinite retries on invalid pages
            return []
