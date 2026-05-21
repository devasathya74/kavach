import contextvars
import logging
import uuid
import functools
import json
import time

# Asynchronous and thread-safe observability context containers
_correlation_id_var = contextvars.ContextVar('correlation_id', default='-')
_user_context_var = contextvars.ContextVar('user_context', default='anonymous')

def get_correlation_id():
    return _correlation_id_var.get()

def set_correlation_id(correlation_id):
    return _correlation_id_var.set(correlation_id)

def get_user_context():
    return _user_context_var.get()

def set_user_context(user_context):
    return _user_context_var.set(user_context)

def reset_correlation_id(token):
    _correlation_id_var.reset(token)

def reset_user_context(token):
    _user_context_var.reset(token)

class CorrelationIDFilter(logging.Filter):
    """
    Logging Filter to inject active Request Correlation ID and User Metadata
    into all emitted log messages across concurrent threads and tasks.
    """
    def filter(self, record):
        record.correlation_id = get_correlation_id()
        record.user_context = get_user_context()
        return True

def log_async_task_duration(task_name):
    """
    Decorator to measure, trace, and log the execution duration
    of asynchronous and background tasks with proper correlation contexts.
    """
    def decorator(func):
        @functools.wraps(func)
        async def wrapper(*args, **kwargs):
            start = time.time()
            task_logger = logging.getLogger('kavach.operational')
            try:
                result = await func(*args, **kwargs)
                elapsed = time.time() - start
                task_logger.info(f"Async Task [{task_name}] completed successfully in {elapsed:.4f}s")
                return result
            except Exception as exc:
                elapsed = time.time() - start
                task_logger.error(
                    f"Async Task [{task_name}] crashed after {elapsed:.4f}s with error [{exc.__class__.__name__}]: {str(exc)}",
                    exc_info=True
                )
                raise exc
        return wrapper
    return decorator

def websocket_exception_interceptor(func):
    """
    Decorator to wrap WebSocket consumer connection and message functions,
    capturing all unhandled exceptions, logging tracebacks to standard loggers,
    and serving standard compliant JSON error frames to clients before clean disconnection.
    """
    @functools.wraps(func)
    async def wrapper(self, *args, **kwargs):
        user = self.scope.get('user')
        pno = getattr(user, 'pno', 'anonymous') if user else 'anonymous'
        role = getattr(user, 'role', 'guest') if user else 'guest'
        
        cid = getattr(self, 'correlation_id', None)
        if not cid:
            cid = f"ws-{pno}-{str(uuid.uuid4())[:8]}"
            self.correlation_id = cid
        
        c_token = set_correlation_id(cid)
        u_token = set_user_context(f"{pno}:{role}")
        try:
            return await func(self, *args, **kwargs)
        except Exception as exc:
            ws_logger = logging.getLogger('kavach.websocket')
            ws_logger.error(
                f"WebSocket Consumer Exception inside {func.__name__} for user {pno}: [{exc.__class__.__name__}] {str(exc)}",
                exc_info=True
            )
            # Emit standard JSON error frame to the client
            try:
                await self.send(text_data=json.dumps({
                    "status": False,
                    "error": {
                        "code": "WEBSOCKET_ERROR",
                        "message": "A critical system exception occurred in the command channel."
                    }
                }))
            except Exception:
                pass
            # Clean disconnection for connect methods to prevent hanging state
            if func.__name__ == 'connect':
                await self.close(code=4011)
        finally:
            reset_correlation_id(c_token)
            reset_user_context(u_token)
    return wrapper

class JSONFormatter(logging.Formatter):
    """
    Structured JSON log formatter for production-ready centralized observability.
    Serializes log record parameters and contexts into single-line JSON blobs.
    """
    def format(self, record):
        log_data = {
            "timestamp": self.formatTime(record, self.datefmt),
            "level": record.levelname,
            "logger": record.name,
            "module": record.module,
            "filename": record.filename,
            "line_number": record.lineno,
            "message": record.getMessage(),
            "correlation_id": getattr(record, "correlation_id", "-"),
            "user_context": getattr(record, "user_context", "anonymous"),
        }
        if record.exc_info:
            log_data["stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(log_data)

