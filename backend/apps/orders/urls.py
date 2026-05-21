from django.urls import path
from .views import OrderListView, AcknowledgeOrderView, AlertListView, AlertAcknowledgeView

urlpatterns = [
    path('', OrderListView.as_view(), name='order-list'),
    path('acknowledge', AcknowledgeOrderView.as_view(), name='order-ack'),
    path('alerts/', AlertListView.as_view(), name='alert-list'),
    path('alerts/ack', AlertAcknowledgeView.as_view(), name='alert-ack'),
]
