import time
import logging

logger = logging.getLogger(__name__)

class CircuitBreaker:
    """
    Cascading Failure Protection.
    Prevents retry storms when external subsystems (Push, WebSocket) are down.
    """
    def __init__(self, name, failure_threshold=5, recovery_timeout=60):
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        
        self.failures = 0
        self.last_failure_time = 0
        self.state = 'CLOSED' # CLOSED, OPEN, HALF_OPEN

    def call(self, func, *args, **kwargs):
        if self.state == 'OPEN':
            if time.time() - self.last_failure_time > self.recovery_timeout:
                self.state = 'HALF_OPEN'
                logger.info(f"CIRCUIT_BREAKER: {self.name} entering HALF_OPEN")
            else:
                raise Exception(f"CIRCUIT_OPEN: {self.name} is currently offline.")

        try:
            result = func(*args, **kwargs)
            self._reset()
            return result
        except Exception as e:
            self._handle_failure()
            raise e

    def _handle_failure(self):
        self.failures += 1
        self.last_failure_time = time.time()
        if self.failures >= self.failure_threshold:
            self.state = 'OPEN'
            logger.error(f"CIRCUIT_OPEN: {self.name} failure threshold reached. State is now OPEN.")

    def _reset(self):
        self.failures = 0
        self.state = 'CLOSED'

# Shared breakers
realtime_breaker = CircuitBreaker("RealtimeGateway")
push_breaker = CircuitBreaker("FCM_Push")
