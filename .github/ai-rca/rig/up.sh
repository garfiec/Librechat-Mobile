#!/usr/bin/env bash
# Start a throwaway LibreChat at the checkout's backendTargetVersion and register a test account.
# Stdout is only KEY=value lines (RIG_URL, RIG_EMAIL, RIG_PASSWORD, RIG_VERSION): the caller appends
# it to $GITHUB_ENV, so anything else goes to stderr.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
port="${RIG_PORT:-3080}"
version="$(sed -n 's/^backendTargetVersion=//p' "$repo/version.properties")"
hex() { openssl rand -hex "$1"; }

# Upstream's own example at the pinned commit: provider keys stay user_provided.
keys='DOMAIN_CLIENT|DOMAIN_SERVER|CREDS_KEY|CREDS_IV|JWT_SECRET|JWT_REFRESH_SECRET|MEILI_MASTER_KEY|ALLOW_EMAIL_LOGIN|ALLOW_REGISTRATION|ALLOW_UNVERIFIED_EMAIL_LOGIN|LOGIN_MAX|REGISTER_MAX'
grep -Ev "^($keys)=" "$repo/upstream/.env.example" > "$here/.env"
cat >> "$here/.env" <<EOF
DOMAIN_CLIENT=http://localhost:$port
DOMAIN_SERVER=http://localhost:$port
CREDS_KEY=$(hex 32)
CREDS_IV=$(hex 16)
JWT_SECRET=$(hex 32)
JWT_REFRESH_SECRET=$(hex 32)
MEILI_MASTER_KEY=$(hex 16)
ALLOW_EMAIL_LOGIN=true
ALLOW_REGISTRATION=true
ALLOW_UNVERIFIED_EMAIL_LOGIN=true
LOGIN_MAX=1000
REGISTER_MAX=1000
EOF
export LIBRECHAT_TAG="v$version" RIG_PORT="$port"
export MEILI_MASTER_KEY="$(sed -n 's/^MEILI_MASTER_KEY=//p' "$here/.env")"
docker compose -f "$here/compose.yml" --project-directory "$here" up -d --quiet-pull >&2

ua="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
url="http://127.0.0.1:$port"
for _ in $(seq 1 90); do
  curl -sf -A "$ua" "$url/api/config" >/dev/null && break
  sleep 2
done
curl -sf -A "$ua" "$url/api/config" >/dev/null || { echo "LibreChat $version did not come up" >&2; exit 1; }

email="switchboard@example.com"
password="$(hex 12)"
body="$(printf '{"name":"Switchboard Test","username":"switchboard","email":"%s","password":"%s","confirm_password":"%s"}' "$email" "$password" "$password")"
status="$(curl -s -o /dev/null -w '%{http_code}' -A "$ua" -H 'Content-Type: application/json' -d "$body" "$url/api/auth/register")"
[ "$status" = 200 ] || [ "$status" = 201 ] || { echo "register failed: $status" >&2; exit 1; }

echo "RIG_URL=$url"
echo "RIG_EMAIL=$email"
echo "RIG_PASSWORD=$password"
echo "RIG_VERSION=$version"
