def get_user_privileges(user):
    """
    Operational Fairness Layer:
    - NO early access to orders (Delivery must be equal).
    - Rewards are in Experience (Zero friction) and Trust Signals.
    """
    try:
        ds = user.discipline_score
        tier = ds.performance_tag
        
        privileges = {
            'friction_delay': 0,
            'can_join_live': True,
            'is_reliable_for_duty': False,
            'ui_mode': 'STANDARD'
        }

        if "ELITE" in tier:
            privileges.update({
                'friction_delay': 0,
                'is_reliable_for_duty': True,
                'ui_mode': 'PREMIUM' # Faster UI, less friction
            })
        elif "HIGH DISCIPLINE" in tier:
            privileges.update({
                'is_reliable_for_duty': True
            })
        elif ds.trust_level == 'RESTRICTED':
            privileges.update({
                'can_join_live': False,
                'friction_delay': 3,
                'ui_mode': 'CONTROLLED'
            })
            
        return privileges
    except:
        return {'can_join_live': True, 'friction_delay': 0, 'ui_mode': 'STANDARD'}
