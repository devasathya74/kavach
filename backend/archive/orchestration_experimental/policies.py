class GovernancePolicy:
    """
    Central Registry of Operational Rules.
    Prevents rule drift across views and workers.
    """
    
    # Device Limits
    MAX_DEVICES = {
        'COMMANDING_OFFICER': 3,
        'PILOT': 2,
        'USER': 1
    }
    
    # Approval Requirements
    SENSITIVE_FIELDS = ['rank', 'role', 'pno', 'service_status', 'is_active']
    
    # Auto-Approval Rules
    AUTO_APPROVE_FIELDS = ['phone', 'email']
    
    @staticmethod
    def requires_ratification(field: str) -> bool:
        return field in GovernancePolicy.SENSITIVE_FIELDS
    
    @staticmethod
    def can_approve(actor, change) -> bool:
        """Enforces Multi-Step Approval (Two-Man Rule)"""
        if actor == change.actor:
            return False
        return actor.role in ['COMMANDING_OFFICER', 'PILOT']

    @staticmethod
    def get_incident_priority(severity: str) -> int:
        return {
            'LOW': 1,
            'MEDIUM': 2,
            'HIGH': 3,
            'CRITICAL': 4
        }.get(severity, 1)
