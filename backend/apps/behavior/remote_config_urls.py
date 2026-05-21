from django.urls import path
from .remote_config_views import RemoteConfigView

urlpatterns = [
    path('', RemoteConfigView.as_view(), name='remote-config'),
]
