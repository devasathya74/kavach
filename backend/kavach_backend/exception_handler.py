import logging
import uuid
from rest_framework.views import exception_handler
from rest_framework.response import Response
from rest_framework import status
from django.db import OperationalError
from django.core.exceptions import ObjectDoesNotExist

logger = logging.getLogger('kavach.errors')

def kavach_exception_handler(exc, context):
    """
    Enterprise Exception Handler for Kavach REST APIs.
    Intercepts and normalizes all standard DRF and unhandled runtime exceptions
    (e.g., db locks, operational timeouts, network disconnects) into a unified JSON schema.
    """
    # Call REST framework's default exception handler first to resolve known API errors
    response = exception_handler(exc, context)
    
    # Retrieve tracing parameters from active HTTP context
    request = context.get('request')
    path = request.path if request else 'unknown'
    method = request.method if request else 'unknown'
    cid = getattr(request, 'correlation_id', '-') if request else '-'
    exc_class_name = exc.__class__.__name__

    if response is not None:
        # standard API exceptions (authentication, authorization, validation, throttles)
        status_code = response.status_code
        drf_code = getattr(exc, "default_code", "API_ERROR").upper()
        
        # friendly message parsing
        if isinstance(response.data, dict):
            if "detail" in response.data:
                message = response.data["detail"]
            else:
                message = "Input validation failed. Please check your data."
        elif isinstance(response.data, list):
            message = response.data[0]
        else:
            message = str(response.data)

        # Code mapping for clean client integrations
        if status_code == 401:
            code = "AUTHENTICATION_FAILED"
        elif status_code == 403:
            code = "PERMISSION_DENIED"
        elif status_code == 404:
            code = "RESOURCE_NOT_FOUND"
        elif status_code == 429:
            code = "RATE_LIMIT_EXCEEDED"
        elif status_code == 400:
            code = "INVALID_REQUEST"
        else:
            code = drf_code

        error_payload = {
            "status": False,
            "error": {
                "code": code,
                "message": message
            }
        }
        
        # Attach nested field-level validation errors for UI resolution
        if status_code == 400 and isinstance(response.data, dict) and "detail" not in response.data:
            error_payload["error"]["details"] = response.data

        response.data = error_payload
        
        # Log client exceptions appropriately
        if status_code == 429:
            logger.warning(f"Rate limiting trigger on {method} {path} | CID: {cid} | Msg: {message}")
        else:
            logger.info(f"Client API error [{exc_class_name}] on {method} {path} | CID: {cid} | Msg: {message}")

    else:
        # Critical Unhandled Exception Layer (500)
        # Catches resource bottlenecks, timeouts, DB lockouts, and cancels
        status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        
        exc_str = str(exc).lower()
        if "locked" in exc_str or "timeout" in exc_str or "deadline" in exc_str or "lockout" in exc_str:
            code = "DATABASE_LOCKOUT_TIMEOUT"
            message = "Resource temporarily locked or query timed out. Please retry the operation."
            logger.critical(
                f"DATABASE RESOURCE LOCKOUT OR TIMEOUT DETECTED: [{exc_class_name}] {str(exc)} on {method} {path} | CID: {cid}",
                exc_info=True
            )
        elif isinstance(exc, ObjectDoesNotExist):
            status_code = status.HTTP_404_NOT_FOUND
            code = "RESOURCE_NOT_FOUND"
            message = "The requested business entity could not be found."
            logger.warning(f"Entity Not Found Exception: [{exc_class_name}] {str(exc)} on {method} {path} | CID: {cid}")
        else:
            code = "INTERNAL_SERVER_ERROR"
            message = "A critical system exception occurred in the application layer."
            logger.critical(
                f"Unhandled Operational Exception: [{exc_class_name}] {str(exc)} on {method} {path} | CID: {cid}",
                exc_info=True
            )

        response = Response({
            "status": False,
            "error": {
                "code": code,
                "message": message
            }
        }, status=status_code)

    return response
