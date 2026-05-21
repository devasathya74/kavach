import time
from .models import DisciplineScore

class DisciplineFrictionMiddleware:
    """
    Introduces 'Behavioral Friction' for RESTRICTED users.
    Delays responses by a few seconds to discourage non-compliance.
    """
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.user.is_authenticated and not request.user.is_staff:
            try:
                ds = request.user.discipline_score
                if ds.trust_level == 'RESTRICTED' and not ds.override_restriction:
                    # Behavioral Friction: Introduce a 3-second delay on every request
                    time.sleep(3)
            except:
                pass
        
        response = self.get_response(request)
        return response
