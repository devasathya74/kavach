"""
Django Settings — Kavach Backend
Edit values marked with # ✏️ EDIT
"""
from pathlib import Path
from datetime import timedelta
from decouple import config

import os

# ── Error Monitoring (Sentry) ──────────────────────────────
SENTRY_DSN = config('SENTRY_DSN', default='')
if SENTRY_DSN:
    import sentry_sdk
    from sentry_sdk.integrations.django import DjangoIntegration
    sentry_sdk.init(
        dsn=SENTRY_DSN,
        integrations=[DjangoIntegration()],
        traces_sample_rate=1.0,
        send_default_pii=False,
    )

BASE_DIR = Path(__file__).resolve().parent.parent

# ── Security ───────────────────────────────────────────────
# ✏️ EDIT: Generate with: python -c "from django.core.management.utils import get_random_secret_key; print(get_random_secret_key())"
SECRET_KEY = config('SECRET_KEY', default='CHANGE_ME_BEFORE_DEPLOY')

DEBUG = config('DEBUG', default=False, cast=bool)

# Pilot Kill Switch (Emergency strict-mode bypass)
KAVACH_PILOT_MODE = config('KAVACH_PILOT_MODE', default=False, cast=bool)

# ✏️ EDIT: Your server domain(s)
if DEBUG:
    ALLOWED_HOSTS = ["*"]
else:
    ALLOWED_HOSTS = ["api.pmsraebareli.online", "localhost", "127.0.0.1"]

CSRF_TRUSTED_ORIGINS = [
    "https://api.pmsraebareli.online",
    "http://localhost:3000"
]


# 🚨 PRODUCTION SAFETY HARD-STOP
if not DEBUG and KAVACH_PILOT_MODE:
    raise RuntimeError("CRITICAL SECURITY ERROR: KAVACH_PILOT_MODE cannot be True in production (DEBUG=False). This bypasses integrity checks and must be disabled before production deployment.")

# 🚨 PRODUCTION SECURITY HARDENING
if not DEBUG:
    SECURE_SSL_REDIRECT = True
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    SECURE_HSTS_SECONDS = 31536000
    SECURE_HSTS_INCLUDE_SUBDOMAINS = True
    SECURE_HSTS_PRELOAD = True
    X_FRAME_OPTIONS = 'DENY'
    # Trust ngrok/Cloudflare proxy headers
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')

# Operational Limits
MAX_UPLOAD_SIZE = 25 * 1024 * 1024 # 25MB safety ceiling
DATA_UPLOAD_MAX_MEMORY_SIZE = MAX_UPLOAD_SIZE
FILE_UPLOAD_MAX_MEMORY_SIZE = MAX_UPLOAD_SIZE

INSTALLED_APPS = [
    'daphne',
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    # Third party
    'rest_framework',
    'rest_framework_simplejwt',
    'rest_framework_simplejwt.token_blacklist',
    'corsheaders',
    'channels',
    # Kavach apps
    'apps.auth_app',
    'apps.training',
    'apps.orders',
    'apps.behavior',
    'apps.admin_panel',
    'apps.monitoring',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'whitenoise.middleware.WhiteNoiseMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'kavach_backend.api_middleware.APIVersioningMiddleware',
    'kavach_backend.session_security.SessionSecurityMiddleware',
    # 'kavach_backend.middleware.RequestIntegrityMiddleware',
]

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

ROOT_URLCONF = 'kavach_backend.urls'
ASGI_APPLICATION = 'kavach_backend.asgi.application'

# ── Database ───────────────────────────────────────────────
if DEBUG:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': config('DB_NAME', default='kavach_db'),
            'USER': config('DB_USER', default='postgres'),
            'PASSWORD': config('DB_PASSWORD', default=''),
            'HOST': config('DB_HOST', default='127.0.0.1'),
            'PORT': config('DB_PORT', default='5432'),
            'CONN_MAX_AGE': 60,
            'OPTIONS': {
                'connect_timeout': 5,
            }
        }
    }

# ── JWT Configuration ──────────────────────────────────────
SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME':  timedelta(minutes=15),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=1),        # 1-day refresh (Hardened)
    'ROTATE_REFRESH_TOKENS':  True,                     # rotating refresh
    'BLACKLIST_AFTER_ROTATION': True,                   # old token invalid
    'ALGORITHM': 'HS256',
    'SIGNING_KEY': config('JWT_SECRET', default=SECRET_KEY),
    'AUTH_HEADER_TYPES': ('Bearer',),
}


REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'EXCEPTION_HANDLER': 'kavach_backend.exception_handler.kavach_exception_handler',
    'DEFAULT_PAGINATION_CLASS': 'apps.auth_app.utils.StandardPagination',
    'PAGE_SIZE': 20,
    'DEFAULT_THROTTLE_CLASSES': [
        'rest_framework.throttling.AnonRateThrottle',
        'rest_framework.throttling.UserRateThrottle',
    ],
    'DEFAULT_THROTTLE_RATES': {
        'anon': '5/min',
        'user': '100/min',
        'otp':  '10/min',
    }
}

APPEND_SLASH = True

# ── CORS ───────────────────────────────────────────────────
CORS_ALLOW_ALL_ORIGINS = DEBUG
CORS_ALLOWED_ORIGINS = config(
    'CORS_ALLOWED_ORIGINS',
    default='http://localhost:3000,http://localhost:5173'
).split(',')

# ── AWS S3 / CDN ───────────────────────────────────────────
# ✏️ EDIT: Your AWS credentials
AWS_ACCESS_KEY_ID     = config('AWS_ACCESS_KEY_ID',     default='')
AWS_SECRET_ACCESS_KEY = config('AWS_SECRET_ACCESS_KEY', default='')
AWS_STORAGE_BUCKET    = config('AWS_STORAGE_BUCKET',    default='kavach-videos')
AWS_REGION            = config('AWS_REGION',            default='ap-south-1')
CLOUDFRONT_DOMAIN     = config('CLOUDFRONT_DOMAIN',     default='')
CLOUDFRONT_KEY_ID     = config('CLOUDFRONT_KEY_ID',     default='')
# ✏️ EDIT: Path to your CloudFront private key file
CLOUDFRONT_PRIVATE_KEY_PATH = config('CLOUDFRONT_PRIVATE_KEY_PATH', default='/etc/kavach/cf_private_key.pem')

# ── Supabase ───────────────────────────────────────────────
SUPABASE_URL = config('SUPABASE_URL', default='https://bamrettslboubdelccmp.supabase.co')
SUPABASE_KEY = config('SUPABASE_SERVICE_KEY', default='')

# ── OTP Settings ───────────────────────────────────────────
OTP_EXPIRY_MINUTES   = 3  # Hardened: Valid for 3 mins only
OTP_LENGTH           = 6

# ── Play Integrity API ─────────────────────────────────────
# ✏️ EDIT: Your Android package name (matches applicationId in build.gradle.kts)
ANDROID_PACKAGE_NAME = config('ANDROID_PACKAGE_NAME', default='com.kavach.app')

# ✏️ EDIT: Google Cloud API key for decoding integrity tokens
# Generate at: console.cloud.google.com → APIs → Play Integrity API → Credentials
# Only needed in production; DEBUG mode uses mock verdicts
PLAY_INTEGRITY_DECODING_KEY = config('PLAY_INTEGRITY_DECODING_KEY', default='')

# Trust window: how long an attestation is valid before re-attestation is required
# Backend checks X-Attested-At header against this limit
INTEGRITY_TRUST_WINDOW_MINUTES = 30

# ── Email Settings (Gmail OTP) ─────────────────────────────
EMAIL_BACKEND       = 'django.core.mail.backends.smtp.EmailBackend'
EMAIL_HOST          = 'smtp.gmail.com'
EMAIL_PORT          = 587
EMAIL_USE_TLS       = True
EMAIL_HOST_USER     = config('EMAIL_HOST_USER', default='')
EMAIL_HOST_PASSWORD = config('EMAIL_HOST_PASSWORD', default='') # Use App Password

# ✏️ EDIT: SMS gateway API key (Not used anymore, replaced by Email)
SMS_API_KEY          = config('SMS_API_KEY', default='')
SMS_SENDER_ID        = config('SMS_SENDER_ID', default='KAVACH')

# ── Behavior Scoring ───────────────────────────────────────
SCORE_DEDUCTIONS = {
    'SEEK_ATTEMPT':     8,
    'QUIZ_FAST_ANSWER': 5,
    'APP_BACKGROUND':   3,
    'DEVICE_MISMATCH':  30,
    'QUIZ_FAIL':        2,
}
SCORE_WARNING_THRESHOLD = 70
SCORE_FLAGGED_THRESHOLD = 50

# ── Training Policy ────────────────────────────────────────
HEARTBEAT_GAP_LIMIT_SECONDS  = 30   # gap > 30s → suspicious
VIDEO_COMPLETION_THRESHOLD   = 0.6  # quiz must take >= 60% of video time
QUIZ_MIN_TIME_RATIO          = 0.6

STATIC_URL  = '/static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'
AUTH_USER_MODEL = 'auth_app.Officer'

# ── Redis & Celery ─────────────────────────────────────────
REDIS_URL = config('REDIS_URL', default='redis://127.0.0.1:6379/1')

CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.locmem.LocMemCache",
        "LOCATION": "unique-snowflake",
    }
}

CELERY_BROKER_URL = config('CELERY_BROKER_URL', default='redis://127.0.0.1:6379/0')
CELERY_ACCEPT_CONTENT = ['json']
CELERY_TASK_SERIALIZER = 'json'

# ── Channels Layer (Redis) ─────────────────────────────────
CHANNEL_LAYERS = {
    "default": {
        "BACKEND": "channels.layers.InMemoryChannelLayer",
    },
}

# ── Firebase Cloud Messaging ──────────────────────────────
FCM_SERVER_KEY = config('FCM_SERVER_KEY', default='')

# ── Session Sharing ────────────────────────────────────────
SESSION_ENGINE = "django.contrib.sessions.backends.cache"
SESSION_CACHE_ALIAS = "default"

# KAVACH Intelligence Engine Kill-Switches
KAVACH_FEATURES = {
    'ml_anomaly': True,
    'graph_intelligence': True,
    'prediction_engine': True,
    'human_review': True,
    'adaptive_enforcement': True,
    'decision_core_active': True
}

# KAVACH Controlled Learning Settings
KAVACH_LEARNING = {
    'MODE': 'SAFE',
    'MAX_WEIGHT_SHIFT': 0.05,
    'ROLLBACK_QUOTA': 2,
    'TRUST_THRESHOLD': 0.6
}

import os
LOGS_DIR = os.path.join(BASE_DIR, 'logs')
os.makedirs(LOGS_DIR, exist_ok=True)

LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'verbose': {
            'format': '{levelname} {asctime} {module} {process:d} {thread:d} {message}',
            'style': '{',
        },
    },
    'handlers': {
        'file': {
            'level': 'INFO',
            'class': 'logging.handlers.TimedRotatingFileHandler',
            'filename': os.path.join(LOGS_DIR, 'kavach_operational.log'),
            'when': 'D',
            'interval': 1,
            'backupCount': 14,
            'formatter': 'verbose',
            'delay': True,  # Fix Windows PermissionError during rollover
        },
        'console': {
            'level': 'INFO',
            'class': 'logging.StreamHandler',
            'formatter': 'verbose',
        },
    },
    'loggers': {
        'django': {
            'handlers': ['file', 'console'],
            'level': 'INFO',
            'propagate': True,
        },
        'auth_app': {
            'handlers': ['file', 'console'],
            'level': 'INFO',
            'propagate': True,
        },
    },
}
