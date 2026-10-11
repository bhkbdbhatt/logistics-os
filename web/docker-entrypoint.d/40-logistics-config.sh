#!/bin/sh
# Renders runtime config from environment variables at container start (ADR-0022).
set -eu

if [ -n "${LOGISTICS_WEB_OIDC_ISSUER:-}" ]; then
  OIDC="oidc: { issuer: \"${LOGISTICS_WEB_OIDC_ISSUER}\", clientId: \"${LOGISTICS_WEB_OIDC_CLIENT_ID:-}\" },"
else
  OIDC=""
fi

cat > /usr/share/nginx/html/config.js <<EOF
window.__LOGISTICS_CONFIG__ = {
  apiBaseUrl: "${LOGISTICS_WEB_API_BASE_URL:-}",
  mode: "${LOGISTICS_WEB_AUTH_MODE:-local}",
  ${OIDC}
};
EOF
