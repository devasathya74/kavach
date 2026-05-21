import json
import urllib.request
import urllib.error
from django.conf import settings

def get_signed_url(bucket_name: str, path: str, expires_in: int = 3600) -> str:
    """Returns a signed URL for a file in Supabase Storage using raw HTTP."""
    url = getattr(settings, 'SUPABASE_URL', '')
    key = getattr(settings, 'SUPABASE_KEY', '')
    
    if not url or not key:
        return ""
        
    api_url = f"{url}/storage/v1/object/sign/{bucket_name}/{path}"
    
    data = json.dumps({"expiresIn": expires_in}).encode('utf-8')
    req = urllib.request.Request(api_url, data=data, method="POST")
    req.add_header("Authorization", f"Bearer {key}")
    req.add_header("Content-Type", "application/json")
    
    try:
        with urllib.request.urlopen(req) as response:
            res_data = json.loads(response.read().decode('utf-8'))
            signed_path = res_data.get('signedURL', '')
            if signed_path:
                return f"{url}{signed_path}"
    except Exception as e:
        print(f"Supabase Sign URL Error: {e}")
        
    return ""
