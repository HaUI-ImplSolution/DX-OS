"""SSO thật qua Keycloak (AUTH_OAUTH) - xem sso/README.md.
Tài khoản admin DB cục bộ (SUPERSET_ADMIN_USERNAME/PASSWORD) vẫn dùng được qua REST API
(provider=db) cho bootstrap_datasources.py, không phụ thuộc AUTH_TYPE.
"""

import base64
import json
import os
from urllib.parse import urlencode

from flask_appbuilder.security.manager import AUTH_OAUTH
from superset.security import SupersetSecurityManager

# api_base_url/access_token_url gọi Keycloak qua hostname nội bộ (server-to-server, trong mạng
# docker); authorize_url dùng hostname trình duyệt - xem "Lưu ý kỹ thuật" trong sso/README.md.
KEYCLOAK_INTERNAL_BASE = "http://keycloak:8080/realms/dxfarm/protocol/openid-connect"
KEYCLOAK_BROWSER_BASE = (
    os.environ["KEYCLOAK_PUBLIC_URL"].rstrip("/") + "/realms/dxfarm/protocol/openid-connect"
)


def _decode_jwt_payload(token: str) -> dict:
    payload_b64 = token.split(".")[1]
    padded = payload_b64 + "=" * (-len(payload_b64) % 4)
    return json.loads(base64.urlsafe_b64decode(padded))


class KeycloakSecurityManager(SupersetSecurityManager):
    def oauth_user_info(self, provider, response=None):
        if provider != "keycloak":
            return {}
        # Đọc thẳng từ access token vừa nhận qua kênh server-to-server, không gọi /userinfo:
        # Keycloak không cố định hostname nên /userinfo từ chối token có issuer theo hostname trình duyệt.
        claims = _decode_jwt_payload(response["access_token"])
        roles = claims.get("realm_access", {}).get("roles", [])
        # Farmers use the mobile app only: without a username Flask-AppBuilder refuses the login.
        if not {"ADMIN", "ENGINEER"} & set(roles):
            return {}
        return {
            "username": claims.get("preferred_username", ""),
            "first_name": claims.get("given_name", ""),
            "last_name": claims.get("family_name", ""),
            "email": claims.get("email", ""),
            "role_keys": claims.get("realm_access", {}).get("roles", []),
        }


CUSTOM_SECURITY_MANAGER = KeycloakSecurityManager

AUTH_TYPE = AUTH_OAUTH
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = "Public"
AUTH_ROLES_SYNC_AT_LOGIN = True
AUTH_ROLES_MAPPING = {
    "ADMIN": ["Admin"],
    "ENGINEER": ["Gamma"],
}

# Đăng xuất ở Superset cũng kết thúc luôn phiên SSO ở Keycloak (không chỉ xoá cookie Superset) -
# để app khác (frontend) tự phát hiện qua session monitoring và đăng xuất theo.
LOGOUT_REDIRECT_URL = f"{KEYCLOAK_BROWSER_BASE}/logout?" + urlencode({
    "client_id": os.environ["KEYCLOAK_SUPERSET_CLIENT_ID"],
    "post_logout_redirect_uri": "http://localhost:8088/login/",
})

OAUTH_PROVIDERS = [
    {
        "name": "keycloak",
        "icon": "fa-key",
        "token_key": "access_token",
        "remote_app": {
            "client_id": os.environ["KEYCLOAK_SUPERSET_CLIENT_ID"],
            "client_secret": os.environ["KEYCLOAK_SUPERSET_CLIENT_SECRET"],
            "client_kwargs": {"scope": "openid profile email"},
            "api_base_url": f"{KEYCLOAK_INTERNAL_BASE}/",
            "access_token_url": f"{KEYCLOAK_INTERNAL_BASE}/token",
            "authorize_url": f"{KEYCLOAK_BROWSER_BASE}/auth",
            "jwks_uri": f"{KEYCLOAK_INTERNAL_BASE}/certs",
        },
    }
]
