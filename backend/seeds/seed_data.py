import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'kavach_backend.settings')
django.setup()

from apps.training.models import Training
from apps.auth_app.models import Officer

# Create a demo officer (if not exists)
officer, created = Officer.objects.get_or_create(
    pno="5678/UP",
    defaults={
        "name": "Vikram Verma",
        "rank": "HC",
        "unit": "Agra Dist",
        "phone": "9876543210"
    }
)
if created:
    officer.set_password("kavach123")
    officer.save()
    print("Demo Officer '5678/UP' created.")

# Create dummy training
t1, _ = Training.objects.get_or_create(
    title="दंगा नियंत्रण प्रशिक्षण",
    defaults={
        "description": "भीड़ नियंत्रण और कानून व्यवस्था",
        "video_path": "riot_control.mp4",
        "duration": 900,
        "is_mandatory": True,
        "status": "PUBLISHED"
    }
)
t2, _ = Training.objects.get_or_create(
    title="साइबर अपराध जागरूकता",
    defaults={
        "description": "डिजिटल सुरक्षा और धोखाधड़ी रोकथाम",
        "video_path": "cyber_awareness.mp4",
        "duration": 480,
        "is_mandatory": True,
        "status": "PUBLISHED"
    }
)
print("Dummy Training data seeded.")
