"""
KAVACH — Django Backend
Complete project structure:

kavach_backend/
├── manage.py
├── requirements.txt
├── kavach_backend/
│   ├── settings.py
│   ├── urls.py
│   └── wsgi.py
└── apps/
    ├── auth_app/          ← OTP login, device binding, JWT
    ├── training/          ← videos, heartbeat, quiz validation
    ├── orders/            ← standing orders, ack with idempotency
    ├── behavior/          ← event ingestion, discipline score
    └── admin_panel/       ← user control, force reset, block
"""
