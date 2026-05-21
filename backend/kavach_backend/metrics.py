import time
import asyncio
import logging
import psutil
from prometheus_client import Gauge, Counter, Histogram, CollectorRegistry

# Setup loggers
logger = logging.getLogger('kavach.operational')
sec_logger = logging.getLogger('kavach.security')

# Base Prometheus Registry
REGISTRY = CollectorRegistry()

# ── WebSocket Metrics ─────────────────────────────────────────────────────────
ACTIVE_WEBSOCKETS = Gauge(
    'kavach_active_websockets', 
    'Number of currently active WebSocket connections.', 
    registry=REGISTRY
)
WEBSOCKET_CONNECTIONS_TOTAL = Counter(
    'kavach_websocket_connections_total', 
    'Total count of WebSocket connection attempts.', 
    ['status'], 
    registry=REGISTRY
)
WEBSOCKET_MESSAGES_RECEIVED = Counter(
    'kavach_websocket_messages_received_total', 
    'Total count of received WebSocket message frames.', 
    registry=REGISTRY
)
WEBSOCKET_MESSAGES_SENT = Counter(
    'kavach_websocket_messages_sent_total', 
    'Total count of transmitted WebSocket message frames.', 
    registry=REGISTRY
)

# ── REST API Metrics ──────────────────────────────────────────────────────────
API_REQUESTS_TOTAL = Counter(
    'kavach_api_requests_total', 
    'Total count of processed HTTP API requests.', 
    ['method', 'path', 'status'], 
    registry=REGISTRY
)
API_REQUEST_LATENCY = Histogram(
    'kavach_api_request_duration_seconds', 
    'REST API request execution latency bounds.', 
    ['method', 'path'], 
    registry=REGISTRY,
    buckets=(0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0)
)

# ── Reliability & System Telemetry ────────────────────────────────────────────
EXCEPTIONS_TOTAL = Counter(
    'kavach_exceptions_total', 
    'Total unhandled or intercepted operational exception occurrences.', 
    ['exception'], 
    registry=REGISTRY
)
EVENT_LOOP_LAG = Gauge(
    'kavach_event_loop_lag_seconds', 
    'Asynchronous event loop execution delay (latency lag).', 
    registry=REGISTRY
)
ACTIVE_ASYNC_TASKS = Gauge(
    'kavach_active_async_tasks', 
    'Total count of active concurrent async tasks.', 
    registry=REGISTRY
)

# Alerting limits
MEMORY_RSS_ALERT_THRESHOLD = 300 * 1024 * 1024  # 300 MB
TASK_SPIKE_ALERT_THRESHOLD = 800
LAG_SPIKE_ALERT_THRESHOLD = 0.5

# ── Custom Collector for Resource Auditing ────────────────────────────────────
class SystemResourceCollector:
    """Collector to query process memory, thread pools, and CPU allocation dynamically."""
    def collect(self):
        try:
            process = psutil.Process()
            rss = process.memory_info().rss
            vms = process.memory_info().vms
            cpu_percent = process.cpu_percent(interval=None)
            num_threads = process.num_threads()
            
            # Memory ceiling audits & alerts
            if rss > MEMORY_RSS_ALERT_THRESHOLD:
                sec_logger.critical(
                    f"CRITICAL MEMORY SPIKE ALERT: Process RSS memory {rss / (1024*1024):.2f}MB "
                    f"exceeded threshold of {MEMORY_RSS_ALERT_THRESHOLD / (1024*1024):.2f}MB!"
                )
                
            # RSS gauge
            rss_g = Gauge('kavach_process_memory_rss_bytes', 'Process Resident Set Size memory.', registry=None)
            rss_g.set(rss)
            yield rss_g.collect()[0]
            
            # VMS gauge
            vms_g = Gauge('kavach_process_memory_vms_bytes', 'Process Virtual Memory Size.', registry=None)
            vms_g.set(vms)
            yield vms_g.collect()[0]
            
            # CPU gauge
            cpu_g = Gauge('kavach_process_cpu_percent', 'Process CPU utilization percentage.', registry=None)
            cpu_g.set(cpu_percent)
            yield cpu_g.collect()[0]
            
            # Threads count gauge
            threads_g = Gauge('kavach_process_threads', 'Total active threads in process.', registry=None)
            threads_g.set(num_threads)
            yield threads_g.collect()[0]
            
        except Exception as e:
            logger.error(f"Failed to collect system metrics: {str(e)}")

REGISTRY.register(SystemResourceCollector())

# ── Asynchronous Event Loop Monitoring Task ──────────────────────────────────
async def start_event_loop_lag_monitor(interval=1.0):
    """
    Background worker that measures event-loop scheduling latency (lag).
    Properly handles cancellation to ensure clean-up when Daphne shuts down.
    """
    logger.info("Initializing event loop lag monitoring thread...")
    while True:
        try:
            start = time.time()
            await asyncio.sleep(interval)
            elapsed = time.time() - start
            lag = max(0.0, elapsed - interval)
            
            EVENT_LOOP_LAG.set(lag)
            
            # Update active task count
            task_count = len(asyncio.all_tasks())
            ACTIVE_ASYNC_TASKS.set(task_count)
            
            # Lag and Task Spike Alerts
            if lag > LAG_SPIKE_ALERT_THRESHOLD:
                logger.warning(
                    f"HIGH EVENT-LOOP LAG DETECTED: Loop scheduled late by {lag:.4f}s. System may be CPU starved."
                )
                
            if task_count > TASK_SPIKE_ALERT_THRESHOLD:
                sec_logger.critical(
                    f"CRITICAL ASYNC TASK SPIKE: Total concurrent tasks {task_count} exceeded limit {TASK_SPIKE_ALERT_THRESHOLD}!"
                )
                
        except asyncio.CancelledError:
            logger.info("Event loop lag monitoring thread gracefully stopped via cancellation request.")
            raise
        except Exception as exc:
            logger.error(f"Exception in event loop lag monitor: {str(exc)}", exc_info=True)
            await asyncio.sleep(interval * 2)
