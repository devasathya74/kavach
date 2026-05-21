from enum import IntEnum

class RoleLevel(IntEnum):
    USER = 10
    PILOT = 80
    ADMIN = 100

ROLE_HIERARCHY = {
    "USER": RoleLevel.USER,
    "PILOT": RoleLevel.PILOT,
    "ADMIN": RoleLevel.ADMIN,
}

from .ranks import get_rank_level

def can_access_personnel(user) -> bool:
    """
    Kavach Core Authority Rule:
    Only ADMIN and PILOT roles can access personnel management features.
    """
    if not user or not user.is_authenticated:
        return False
    return user.role in ["ADMIN", "PILOT"]

def can_manage(actor, target) -> bool:
    """
    Centralized Authority Engine for Officer Management.
    Determines if 'actor' has sufficient authority over 'target'.
    """
    if not actor.is_authenticated:
        return False
        
    actor_role = actor.role
    target_role = target.role
    
    # 1. ADMIN Bypass: Absolute Command
    if actor_role == "ADMIN":
        return True
        
    # 2. PILOT Authority: Chain of Command
    if actor_role == "PILOT":
        actor_rank = actor.profile.rank.code if hasattr(actor, 'profile') and actor.profile.rank else None
        target_rank = target.profile.rank.code if hasattr(target, 'profile') and target.profile.rank else None
        
        if not (actor_rank and target_rank): return False
        
        # Institutional Rule: Cannot manage same or higher rank, or an ADMIN
        if target_role == "ADMIN": return False
        if get_rank_level(target_rank) >= get_rank_level(actor_rank): return False
        
        return True
            
    # 3. All other cases denied
    return False

def requires_approval(action: str, actor_role: str, target_role: str) -> bool:
    """
    Determines if an action requires formal CO ratification.
    """
    critical_actions = ['DELETE', 'SUSPEND', 'ROLE_UPGRADE', 'RANK_CHANGE']
    
    if action in critical_actions:
        # ADMIN and PILOT are high-authority roles, they can apply changes directly
        if actor_role in ["ADMIN", "PILOT"]:
            return False
        return True
        
    return False
