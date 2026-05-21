import time
import asyncio
import logging
import functools

logger = logging.getLogger('kavach.resilience')

class CircuitBreakerOpenException(Exception):
    """Exception raised when a call is fast-failed while the Circuit Breaker is in the OPEN state."""
    pass

class CircuitBreaker:
    """
    Thread-safe and async-safe three-state Circuit Breaker bulkhead pattern.
    States:
      - CLOSED: Requests flow normally. Failures increment failure count.
      - OPEN: Requests are fast-failed. Stays open until recovery_timeout expires.
      - HALF-OPEN: Allows a trial request. If successful, transitions to CLOSED. If fails, returns to OPEN.
    """
    def __init__(self, name, failure_threshold=5, recovery_timeout=30):
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.state = "CLOSED" # CLOSED, OPEN, HALF-OPEN
        self.failure_count = 0
        self.last_failure_time = 0.0
        self._lock = asyncio.Lock()

    def __call__(self, func):
        if asyncio.iscoroutinefunction(func):
            @functools.wraps(func)
            async def wrapper(*args, **kwargs):
                await self.before_call()
                try:
                    res = await func(*args, **kwargs)
                    await self.on_success()
                    return res
                except Exception as e:
                    await self.on_failure(e)
                    raise e
            return wrapper
        else:
            @functools.wraps(func)
            def wrapper(*args, **kwargs):
                # Synchronous fallback
                self.before_call_sync()
                try:
                    res = func(*args, **kwargs)
                    self.on_success_sync()
                    return res
                except Exception as e:
                    self.on_failure_sync(e)
                    raise e
            return wrapper

    async def before_call(self):
        async with self._lock:
            self._check_state()

    def before_call_sync(self):
        self._check_state()

    def _check_state(self):
        now = time.time()
        if self.state == "OPEN":
            if now - self.last_failure_time > self.recovery_timeout:
                self.state = "HALF-OPEN"
                logger.warning(f"Circuit Breaker [{self.name}] entered HALF-OPEN trial state.")
            else:
                raise CircuitBreakerOpenException(f"Circuit Breaker [{self.name}] is OPEN. Fast-failing downstream call.")

    async def on_success(self):
        async with self._lock:
            self._mark_success()

    def on_success_sync(self):
        self._mark_success()

    def _mark_success(self):
        if self.state == "HALF-OPEN":
            self.state = "CLOSED"
            self.failure_count = 0
            logger.info(f"Circuit Breaker [{self.name}] successfully recovered to CLOSED state.")

    async def on_failure(self, exc):
        async with self._lock:
            self._mark_failure(exc)

    def on_failure_sync(self, exc):
        self._mark_failure(exc)

    def _mark_failure(self, exc):
        self.failure_count += 1
        self.last_failure_time = time.time()
        if self.state in ["CLOSED", "HALF-OPEN"] and self.failure_count >= self.failure_threshold:
            self.state = "OPEN"
            logger.critical(
                f"Circuit Breaker [{self.name}] TRIPPED to OPEN state after {self.failure_count} consecutive failures. "
                f"Triggering exception: {exc.__class__.__name__}: {str(exc)}"
            )
