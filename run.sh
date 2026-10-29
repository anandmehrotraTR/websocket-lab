#!/usr/bin/env bash
#
# Build, launch and verify the websocket-lab stack.
#
#   ./run.sh           build + (re)start everything, then verify
#   ./run.sh verify     skip the build, just re-run the checks
#   ./run.sh down       tear the stack down
#
set -euo pipefail

# Docker Desktop on macOS does not always put the CLI on PATH.
if command -v docker >/dev/null 2>&1; then
    DOCKER=docker
elif [[ -x /Applications/Docker.app/Contents/Resources/bin/docker ]]; then
    DOCKER=/Applications/Docker.app/Contents/Resources/bin/docker
else
    echo "docker CLI not found. Is Docker Desktop installed?" >&2
    exit 1
fi

cd "$(dirname "$0")"

BASE_URL=http://localhost:8080
CLIENT_1=websocket-lab-ws-client-1-1   # on ws-server-1
CLIENT_3=websocket-lab-ws-client-3-1   # on ws-server-2

step()  { printf '\n\033[1;34m==>\033[0m %s\n' "$1"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; }
die()   { fail "$1"; exit 1; }

# Docker Desktop listens on its own socket, not /var/run/docker.sock.
ensure_docker_context() {
    if ! $DOCKER info >/dev/null 2>&1; then
        if $DOCKER context inspect desktop-linux >/dev/null 2>&1; then
            step "Docker API unreachable - switching to the desktop-linux context"
            $DOCKER context use desktop-linux >/dev/null
        fi
        $DOCKER info >/dev/null 2>&1 || die "Cannot reach the Docker daemon. Is Docker Desktop running?"
    fi
}

if [[ "${1:-up}" == "down" ]]; then
    ensure_docker_context
    step "Tearing down"
    $DOCKER compose down
    ok "stopped"
    exit 0
fi

ensure_docker_context

if [[ "${1:-up}" != "verify" ]]; then
    step "Building jars"
    ./gradlew build -x test -q
    ok "gradle build"

    step "Building images and starting containers"
    $DOCKER compose up --build -d

    # nginx.conf is a read-only bind mount, so compose sees no reason to
    # recreate the container when only the config changed. Without this
    # restart a freshly edited nginx.conf is silently ignored.
    step "Restarting nginx to pick up nginx.conf"
    $DOCKER compose restart nginx >/dev/null
    ok "nginx restarted"
fi

step "Waiting for the dashboard to answer"
for i in $(seq 1 60); do
    if curl -sf -o /dev/null "$BASE_URL/api/clients"; then
        ok "$BASE_URL is up"
        break
    fi
    [[ $i -eq 60 ]] && die "dashboard never came up - try: $DOCKER compose logs nginx ws-server-1"
    sleep 2
done

step "Waiting for clients to register"
# Clients retry on their own schedule; give all 5 time to land in Redis.
for i in $(seq 1 45); do
    COUNT=$(curl -s "$BASE_URL/api/clients" | tr ',' '\n' | grep -c sessionId || true)
    [[ "$COUNT" -ge 5 ]] && break
    [[ $i -eq 45 ]] && fail "only $COUNT of 5 clients registered after 90s"
    sleep 2
done

step "Clients by server"
if command -v jq >/dev/null 2>&1; then
    curl -s "$BASE_URL/api/clients" \
        | jq -r 'to_entries | sort_by(.key) | .[] | "  \(.key): \(.value | length) client(s)"'
else
    curl -s "$BASE_URL/api/clients"
    echo
    echo "  (install jq for a nicer breakdown: brew install jq)"
fi

step "Testing targeted delivery across two servers"
# The interesting path: one client per server, so the message must cross
# Redis to reach a server that did not receive the HTTP request.
if command -v jq >/dev/null 2>&1; then
    REGISTRY=$(curl -s "$BASE_URL/api/clients")
    T1=$(echo "$REGISTRY" | jq -r '.["ws-server-1"][0].sessionId // empty')
    T2=$(echo "$REGISTRY" | jq -r '.["ws-server-2"][0].sessionId // empty')

    if [[ -z "$T1" || -z "$T2" ]]; then
        fail "need a client on both ws-server-1 and ws-server-2; skipping"
    else
        MARKER="targeted-probe-$(date +%s)"
        PAYLOAD=$(jq -cn --arg m "$MARKER" --arg a "$T1" --arg b "$T2" \
            '{type:"targeted", targets:[$a,$b], message:$m}')

        curl -sf -X POST -G "$BASE_URL/api/publish" \
            --data-urlencode "message=$PAYLOAD" >/dev/null \
            || die "POST /api/publish failed"
        ok "published to ${T1:0:8}… (server-1) and ${T2:0:8}… (server-2)"

        sleep 3
        for c in "$CLIENT_1" "$CLIENT_3"; do
            if $DOCKER logs "$c" 2>&1 | grep -q "$MARKER"; then
                ok "$c received it"
            else
                fail "$c did NOT receive it"
            fi
        done

        step "Checking the targeted message did not leak to everyone"
        LEAKED=0
        for n in 2 4 5; do
            c="websocket-lab-ws-client-$n-1"
            if $DOCKER logs "$c" 2>&1 | grep -q "$MARKER"; then
                fail "$c received a message it was not targeted with"
                LEAKED=1
            fi
        done
        [[ $LEAKED -eq 0 ]] && ok "non-targeted clients were correctly skipped"
    fi
else
    fail "jq not installed - skipping the targeted-delivery test (brew install jq)"
fi

step "Done"
echo "  Dashboard: $BASE_URL"
echo "  Logs:      $DOCKER compose logs -f ws-server-1"
echo "  Teardown:  ./run.sh down"
