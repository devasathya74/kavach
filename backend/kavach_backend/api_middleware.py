from django.http import JsonResponse

class APIVersioningMiddleware:
    """
    Enforces that all API requests use the /api/v1/ prefix.
    Prevents unauthorized access to legacy or unversioned endpoints.
    """
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        path = request.path
        
        # Only enforce on /api/ paths, excluding health check and v1/v2 versions
        if path.startswith('/api/') and \
           not path.startswith('/api/v1/') and \
           not path.startswith('/api/v2/') and \
           path != '/api/health':
            return JsonResponse({
                "error": "API Versioning Violation",
                "message": "Legacy or unversioned API access is prohibited. Please use /api/v1/",
                "code": "VERSION_MISMATCH"
            }, status=403)
            
        return self.get_response(request)
