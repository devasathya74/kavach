from django.conf import settings

class PilotController:
    """
    Enforces the KAVACH PILOT CHECKLIST rules based on the active phase.
    """
    PHASES = {
        0: "PRE-LAUNCH",
        1: "SHADOW MODE",
        2: "CONTROLLED MODE",
        3: "LIMITED AUTHORITY",
        4: "FULL PILOT"
    }

    @classmethod
    def get_current_phase(cls):
        return getattr(settings, 'KAVACH_PILOT_PHASE', 0)

    @classmethod
    def is_action_allowed(cls, action_type, impact='LOW'):
        phase = cls.get_current_phase()
        
        if phase == 0: return False # All actions blocked
        
        if phase == 1: # SHADOW MODE
             return False # No real actions, just log

        if phase == 2: # CONTROLLED MODE
             if action_type in ['ALERT', 'SOFT_FRICTION', 'MONITORING']:
                 return True
             return False

        if phase == 3: # LIMITED AUTHORITY
             if impact == 'HIGH' or action_type == 'RESTRICTION':
                 return False
             return True

        if phase == 4: # FULL PILOT
             return True
             
        return False
