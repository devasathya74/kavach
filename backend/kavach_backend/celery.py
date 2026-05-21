import os
from celery import Celery

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')

app = Celery('kavach_backend')
app.config_from_object('django.conf:settings', namespace='CELERY')
app.autodiscover_tasks()
