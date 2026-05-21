from rest_framework.views import exception_handler
from rest_framework.response import Response
from rest_framework import status

def kavach_exception_handler(exc, context):
    # Call REST framework's default exception handler first,
    # to get the standard error response.
    response = exception_handler(exc, context)

    from django.utils import timezone
    import uuid

    # If an exception occurs that is not handled by the standard handler,
    # response will be None.
    if response is not None:
        custom_data = {
            "success": False,
            "code": getattr(exc, "default_code", "ERROR"),
            "message": response.data.get("detail", str(response.data)),
            "trace_id": str(uuid.uuid4())[:8],
            "timestamp": timezone.now().isoformat()
        }
        # Simplify common error formats
        if isinstance(response.data, dict) and "detail" not in response.data:
             # Handle field-specific validation errors
             custom_data["code"] = "VALIDATION_ERROR"
             custom_data["errors"] = response.data
             custom_data["message"] = "Invalid input data."

        response.data = custom_data
    else:
        # For unhandled server errors (500)
        return Response({
            "success": False,
            "code": "SERVER_ERROR",
            "message": "A critical system error occurred. Reporting to Command Center."
        }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

    return response
