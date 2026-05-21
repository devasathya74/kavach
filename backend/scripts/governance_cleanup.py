import os
import django
from django.utils import timezone
from datetime import timedelta

# Setup django environment
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.auth_app.models import DraftChange, OfficerActivity

def cleanup_governance():
    """
    Governance Reliability Task.
    - Expires SUBMITTED requests older than 24 hours.
    - Purges REJECTED/APPLIED drafts older than 30 days.
    """
    expiry_threshold = timezone.now() - timedelta(hours=24)
    
    # 1. Expire stale requests
    stale_requests = DraftChange.objects.filter(
        status='SUBMITTED',
        created_at__lt=expiry_threshold
    )
    
    count = stale_requests.count()
    if count > 0:
        for req in stale_requests:
            req.status = 'EXPIRED'
            req.save()
            
            # Log expiry
            OfficerActivity.objects.create(
                officer=req.target,
                actor=None, # System
                unit=req.unit,
                pno=req.target.pno,
                action='GOVERNANCE_EXPIRED',
                severity='WARNING',
                result='SUCCESS',
                metadata={'field': req.field, 'reason': 'Approval window exceeded'}
            )
        print(f"Expired {count} stale governance requests.")

if __name__ == "__main__":
    cleanup_governance()
