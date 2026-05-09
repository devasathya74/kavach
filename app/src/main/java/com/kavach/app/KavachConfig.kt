package com.kavach.app

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           KAVACH — MANUAL CONFIGURATION FILE                ║
 * ║                                                              ║
 * ║  इस file में सभी values हैं जो आपको manually edit करनी हैं  ║
 * ║  Deploy करने से पहले हर एक value को fill करें।              ║
 * ║                                                              ║
 * ║  File path:                                                  ║
 * ║  app/src/main/java/com/kavach/app/KavachConfig.kt           ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
object KavachConfig {
    /**
     * Pilot Mode — Production-ready backend but relaxed client-side enforcement
     * for early phase monitoring and debugging.
     */
    const val PILOT_MODE = true



    // ══════════════════════════════════════════════════════════════
    // 1. SERVER / API CONFIGURATION
    // ══════════════════════════════════════════════════════════════

    /**
     * आपके backend server का URL।
     * ध्यान दें: अंत में "/" जरूर लगाएं।
     *
     * Examples:
     *   "https://kavach.yourforce.in/api/"
     *   "https://api.kavach.gov.in/v1/"
     *
     * ✏️ EDIT THIS:
     */
    /**
     * आपके backend server का URL।
     */
    const val BASE_URL = "https://api.pmsraebareli.online/"

    /**
     * Debug/Development के लिए local server URL।
     */
    const val BASE_URL_DEBUG = "http://192.168.1.6:8000/api/v1/"


    // ══════════════════════════════════════════════════════════════
    // 2. SSL CERTIFICATE PINNING
    // ══════════════════════════════════════════════════════════════

    /**
     * आपके server के TLS certificate का SHA-256 hash।
     *
     * कैसे निकालें (Terminal में run करें):
     *   openssl s_client -connect YOUR_SERVER.com:443 -servername YOUR_SERVER.com \
     *   </dev/null 2>/dev/null | openssl x509 -pubkey -noout | \
     *   openssl pkey -pubin -outform der | \
     *   openssl dgst -sha256 -binary | openssl enc -base64
     *
     * इसे network_security_config.xml में भी paste करें।
     * Format: "sha256/<BASE64_HASH>"
     *
     * ✏️ EDIT THIS:
     */
    const val SSL_PIN_PRIMARY = "sha256/REPLACE_WITH_YOUR_PRIMARY_CERT_HASH="

    /**
     * Backup certificate pin — certificate rotate करते समय lockout से बचाने के लिए।
     * Let's Encrypt root या आपके CA का pin यहाँ लगाएं।
     *
     * ✏️ EDIT THIS:
     */
    const val SSL_PIN_BACKUP = "sha256/REPLACE_WITH_YOUR_BACKUP_CERT_HASH="

    /**
     * वह domain जिसे pin करना है।
     * BASE_URL के domain से match होना चाहिए।
     *
     * ✏️ EDIT THIS:
     */
    const val PINNED_DOMAIN = "api.pmsraebareli.online"


    // ══════════════════════════════════════════════════════════════
    // 3. VIDEO STREAMING CONFIGURATION
    // ══════════════════════════════════════════════════════════════

    /**
     * CDN का base URL जहाँ training videos store हैं।
     *
     * Options:
     *   AWS CloudFront : "https://XXXX.cloudfront.net/"
     *   Cloudflare R2  : "https://pub-XXXX.r2.dev/"
     *   BunnyCDN       : "https://YOURZONE.b-cdn.net/"
     *   Direct S3      : "https://YOUR-BUCKET.s3.ap-south-1.amazonaws.com/"
     *
     * ✏️ EDIT THIS:
     */
    const val CDN_BASE_URL = "https://YOUR_CDN_DOMAIN.cloudfront.net/"

    /**
     * Signed URL का expiry time (seconds)।
     * Video को इतने समय तक access दिया जाएगा।
     * 3600 = 1 hour (recommended)
     *
     * ✏️ EDIT THIS (if needed):
     */
    const val SIGNED_URL_EXPIRY_SECONDS = 3600


    // ══════════════════════════════════════════════════════════════
    // 4. APP IDENTIFICATION
    // ══════════════════════════════════════════════════════════════

    /**
     * आपके app का package name।
     * app/build.gradle.kts में "namespace" से match होना चाहिए।
     *
     * ✏️ EDIT THIS (if changed from default):
     */
    const val APP_PACKAGE_NAME = "com.kavach.app"

    /**
     * App version — server logs में identify करने के लिए।
     * हर release पर update करें।
     *
     * ✏️ EDIT THIS on every release:
     */
    const val APP_VERSION = "1.0.0"
    const val APP_VERSION_CODE = 1


    // ══════════════════════════════════════════════════════════════
    // 5. SECURITY POLICY SETTINGS
    // ══════════════════════════════════════════════════════════════

    /**
     * Maximum failed login attempts before temporary lock।
     * इसके बाद X minutes wait करना होगा।
     *
     * ✏️ EDIT THIS (default: 5):
     */
    const val MAX_LOGIN_ATTEMPTS = 5

    /**
     * Login lockout duration (minutes)।
     *
     * ✏️ EDIT THIS (default: 15 minutes):
     */
    const val LOGIN_LOCKOUT_MINUTES = 15L

    /**
     * Session idle timeout (minutes)।
     * इतने समय तक कोई interaction नहीं → automatic logout।
     * ✏️ PILOT FIX: 30 minutes for operational field usage.
     */
    const val SESSION_TIMEOUT_MINUTES = 30L

    /**
     * Session timeout warning (minutes)।
     * Logout से पहले warning dialog कितनी देर पहले दिखाएं।
     */
    const val SESSION_TIMEOUT_WARNING_MINUTES = 25L

    /**
     * क्या rooted device पर app block करनी है?
     * ✏️ PILOT FIX: false (Observe first, punish later).
     */
    const val BLOCK_ROOTED_DEVICES = false

    /**
     * क्या emulator पर app block करनी है?
     * ✏️ PILOT FIX: false (Allow during pilot monitoring).
     */
    const val BLOCK_EMULATORS = false


    // ══════════════════════════════════════════════════════════════
    // 6. TRAINING / QUIZ POLICY
    // ══════════════════════════════════════════════════════════════

    /**
     * Quiz pass करने के लिए minimum score (0–100)।
     * 70 = 70% correct answers required
     *
     * ✏️ EDIT THIS (default: 70):
     */
    const val QUIZ_PASS_PERCENTAGE = 70

    /**
     * Maximum quiz attempts allowed।
     * इसके बाद training reset करनी होगी (admin action)।
     *
     * ✏️ EDIT THIS (default: 3):
     */
    const val QUIZ_MAX_ATTEMPTS = 3

    /**
     * प्रत्येक quiz question के लिए minimum reading time (seconds)।
     * इससे कम समय में answer → rejected + behavior event logged।
     *
     * ✏️ EDIT THIS (default: 5 seconds):
     */
    const val QUIZ_MIN_ANSWER_TIME_SECONDS = 5L

    /**
     * Heartbeat interval during video playback (milliseconds)।
     * इतने समय पर एक heartbeat server को भेजा जाएगा।
     *
     * ✏️ EDIT THIS (default: 15 seconds = 15000ms):
     */
    const val HEARTBEAT_INTERVAL_MS = 15_000L

    /**
     * Video completion threshold।
     * ✏️ PILOT FIX: 0.6 (Reduced to prevent false positives in early phase).
     */
    const val VIDEO_COMPLETION_THRESHOLD = 0.6


    // ══════════════════════════════════════════════════════════════
    // 7. BEHAVIOR SCORING THRESHOLDS
    // ══════════════════════════════════════════════════════════════

    /**
     * Discipline score जिससे कम होने पर user को "WARNING" grade मिलेगा।
     *
     * ✏️ EDIT THIS (default: 70):
     */
    const val SCORE_WARNING_THRESHOLD = 70

    /**
     * Discipline score जिससे कम होने पर user "FLAGGED" होगा।
     *
     * ✏️ EDIT THIS (default: 50):
     */
    const val SCORE_FLAGGED_THRESHOLD = 50

    /**
     * SEEK_ATTEMPT count जिससे ज़्यादा होने पर admin alert।
     *
     * ✏️ EDIT THIS (default: 5):
     */
    const val SEEK_ATTEMPT_ALERT_COUNT = 5

    /**
     * Fast answer count जिससे ज़्यादा → quiz re-test mandatory।
     *
     * ✏️ EDIT THIS (default: 3):
     */
    const val FAST_ANSWER_ALERT_COUNT = 3


    // ══════════════════════════════════════════════════════════════
    // 8. BACKGROUND SYNC SCHEDULE
    // ══════════════════════════════════════════════════════════════

    /**
     * WorkManager sync interval (minutes)।
     * Pending acks और behavior events इतने समय पर upload होंगे।
     * Minimum allowed by Android: 15 minutes।
     *
     * ✏️ EDIT THIS (default: 15 minutes):
     */
    const val SYNC_INTERVAL_MINUTES = 15L

    /**
     * WorkManager backoff delay on failure (minutes)।
     *
     * ✏️ EDIT THIS (default: 5 minutes):
     */
    const val SYNC_BACKOFF_MINUTES = 5L


    // ══════════════════════════════════════════════════════════════
    // 9. WATERMARK CONFIGURATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Watermark position shift interval (seconds)।
     * इतने समय पर watermark अपनी position बदलेगा।
     *
     * ✏️ EDIT THIS (default: 5 seconds):
     */
    const val WATERMARK_SHIFT_INTERVAL_SECONDS = 5L

    /**
     * Watermark minimum alpha (transparency)।
     * 0.0 = invisible, 1.0 = fully opaque
     *
     * ✏️ EDIT THIS (default: 0.25):
     */
    const val WATERMARK_ALPHA_MIN = 0.25f

    /**
     * Watermark maximum alpha।
     *
     * ✏️ EDIT THIS (default: 0.45):
     */
    const val WATERMARK_ALPHA_MAX = 0.45f


    // ══════════════════════════════════════════════════════════════
    // 10. NETWORK TIMEOUTS
    // ══════════════════════════════════════════════════════════════

    /**
     * API request connect timeout (seconds)।
     *
     * ✏️ EDIT THIS (default: 30):
     */
    const val NETWORK_CONNECT_TIMEOUT_SECONDS = 30L

    /**
     * API request read timeout (seconds)।
     *
     * ✏️ EDIT THIS (default: 30):
     */
    const val NETWORK_READ_TIMEOUT_SECONDS = 30L

    /**
     * Token refresh timeout (seconds) — shorter than normal।
     * Refresh endpoint पर ज़्यादा wait नहीं करना।
     *
     * ✏️ EDIT THIS (default: 15):
     */
    const val REFRESH_TIMEOUT_SECONDS = 15L


    // ══════════════════════════════════════════════════════════════
    // 11. ORGANIZATION DETAILS (UI में दिखेंगे)
    // ══════════════════════════════════════════════════════════════

    /**
     * App में दिखने वाला organization name।
     *
     * ✏️ EDIT THIS:
     */
    const val ORG_NAME = "भारतीय पुलिस बल"

    /**
     * App में दिखने वाला department/unit name।
     *
     * ✏️ EDIT THIS:
     */
    const val DEPT_NAME = "प्रशिक्षण एवं संचालन प्रभाग"

    /**
     * Support contact (error screens पर दिखेगा)।
     *
     * ✏️ EDIT THIS:
     */
    const val SUPPORT_CONTACT = "संबंधित अधिकारी से संपर्क करें"

    /**
     * Admin web dashboard URL (profile screen में link के लिए)।
     *
     * ✏️ EDIT THIS:
     */
    const val ADMIN_DASHBOARD_URL = "https://admin.YOUR_SERVER_DOMAIN.com"
}
