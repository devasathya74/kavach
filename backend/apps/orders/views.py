from rest_framework.views import APIView
from rest_framework.response import Response
from .models import Order, OrderAck
from kavach_backend.supabase_client import get_signed_url

class OrderListView(APIView):
    def get(self, request):
        orders = Order.objects.all()
        data = []
        for o in orders:
            is_ack = OrderAck.objects.filter(user=request.user, order=o).exists()
            signed_url = get_signed_url("orders-media", o.image_path) if o.image_path else None
            
            if signed_url:
                from apps.auth_app.views import _audit
                _audit(request.user, 'SIGNED_URL_ORDER', request, order_id=str(o.id), image_path=o.image_path)
            
            data.append({
                "id": str(o.id),
                "title": o.title,
                "contentText": o.content,
                "imageUrl": signed_url,
                "isMandatory": o.is_mandatory,
                "issuedBy": o.issued_by,
                "createdAt": int(o.created_at.timestamp() * 1000),
                "isAcknowledged": is_ack
            })
        return Response({'status': 'success', 'data': data})

class AcknowledgeOrderView(APIView):
    def post(self, request):
        order_id = request.data.get('order_id')
        device_id = request.data.get('device_id', '')
        read_duration = request.data.get('read_duration', 0)
        idempotency_key = request.data.get('idempotency_key', None)
        
        try:
            order = Order.objects.get(id=order_id)
        except Order.DoesNotExist:
            return Response({'status': 'error', 'message': 'Order not found'}, status=404)
            
        if idempotency_key and OrderAck.objects.filter(idempotency_key=idempotency_key).exists():
            return Response({'status': 'success', 'message': 'Already acknowledged (idempotent)'})
            
        OrderAck.objects.update_or_create(
            user=request.user,
            order=order,
            defaults={
                'device_id': device_id,
                'read_duration_ms': read_duration,
                'idempotency_key': idempotency_key
            }
        )
        return Response({'status': 'success'})

from .models import Alert, AlertAck
from django.db.models import Q

class AlertListView(APIView):
    """
    GET /api/orders/alerts
    Returns unacknowledged alerts for the user's unit (or ALL).
    """
    def get(self, request):
        user_unit = request.user.unit if hasattr(request.user, 'unit') and request.user.unit else None
        
        # Get active alerts for this unit or ALL
        query = Q(target_unit__isnull=True) | Q(target_unit='')
        if user_unit:
            query = query | Q(target_unit=user_unit)
            
        alerts = Alert.objects.filter(query)
        
        data = []
        for a in alerts:
            is_ack = AlertAck.objects.filter(user=request.user, alert=a).exists()
            if not is_ack:
                data.append({
                    "id": str(a.id),
                    "type": a.type,
                    "title": a.title,
                    "content": a.content,
                    "createdAt": int(a.created_at.timestamp() * 1000)
                })
        return Response({'status': 'success', 'data': data})

class AlertAcknowledgeView(APIView):
    """
    POST /api/orders/alerts/ack
    { alert_id, read_time }
    Validates minimum read time depending on alert type.
    """
    def post(self, request):
        alert_id = request.data.get('alert_id')
        read_time = request.data.get('read_time', 0)
        
        try:
            alert = Alert.objects.get(id=alert_id)
        except Alert.DoesNotExist:
            return Response({'status': 'error', 'message': 'Alert not found'}, status=404)
            
        # Security: Force minimum read time verification
        if alert.type == 'CRITICAL' and read_time < 2:
            return Response({'status': 'error', 'message': 'Minimum read time not met. Acknowledgment rejected.'}, status=400)
            
        AlertAck.objects.update_or_create(
            user=request.user,
            alert=alert,
            defaults={
                'read_time': read_time,
                'is_verified': True
            }
        )
        return Response({'status': 'success'})
