class IncidentPolicy:
    """
    Operational Incident Semantics.
    Defines auto-behaviors based on severity.
    """
    
    SEVERITY_LEVELS = {
        'LOW': {'escalate': False, 'notify': 'UNIT_INFO', 'realtime': False},
        'MEDIUM': {'escalate': False, 'notify': 'UNIT_URGENT', 'realtime': True},
        'HIGH': {'escalate': True, 'notify': 'BATTALION_URGENT', 'realtime': True},
        'CRITICAL': {'escalate': True, 'notify': 'FORCE_BROADCAST', 'realtime': True}
    }

    @staticmethod
    def get_config(severity: str) -> dict:
        return IncidentPolicy.SEVERITY_LEVELS.get(severity, IncidentPolicy.SEVERITY_LEVELS['LOW'])
    
    @staticmethod
    def generate_incident_id(unit_code: str) -> str:
        import time
        import random
        return f"INC-{unit_code}-{int(time.time())}-{random.randint(100, 999)}"
