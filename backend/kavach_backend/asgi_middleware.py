import logging
import asyncio
from kavach_backend.metrics import start_event_loop_lag_monitor, EXCEPTIONS_TOTAL

class KavachASGIExceptionMiddleware:
    """
    ASGI Middleware to catch and log unhandled asynchronous exceptions,
    including Redis disconnects, Channel layer timeouts, and database exhaustions.
    Additionally boots background event loop lag monitoring.
    """
    def __init__(self, inner):
        self.inner = inner
        self._lag_monitor_started = False
        self._lock = asyncio.Lock()

    async def __call__(self, scope, receive, send):
        if not self._lag_monitor_started:
            async with self._lock:
                if not self._lag_monitor_started:
                    self._lag_monitor_started = True
                    try:
                        asyncio.create_task(start_event_loop_lag_monitor())
                    except Exception as e:
                        logging.getLogger('kavach.errors').error(
                            f"Failed to spawn event loop lag monitor: {str(e)}"
                        )

        try:
            await self.inner(scope, receive, send)
        except Exception as exc:
            logger = logging.getLogger('kavach.errors')
            exc_name = exc.__class__.__name__
            logger.critical(
                f"ASGI Unhandled Exception: [{exc_name}] {str(exc)} on path {scope.get('path', 'unknown')}",
                exc_info=True
            )
            
            # Increment unhandled exceptions count
            try:
                EXCEPTIONS_TOTAL.labels(exception=exc_name).inc()
            except Exception:
                pass

            # WebSocket specific recovery payload
            if scope.get('type') == 'websocket':
                try:
                    await send({
                        "type": "websocket.close",
                        "code": 4011
                    })
                except Exception:
                    pass
            raise exc

