#!/bin/sh
# Keep provider output private: authentication errors can contain credentials.
# Never enable shell tracing here.
set -eu
umask 077

daemon_pid=
java_pid=
runtime_dir=

log() { printf '%s\n' "[startup] $*" >&2; }
fail() { log "ERROR: $*"; exit 1; }

cleanup() {
    trap - EXIT INT TERM
    if [ -n "$java_pid" ]; then kill "$java_pid" 2>/dev/null || :; fi
    if [ -n "$daemon_pid" ]; then kill "$daemon_pid" 2>/dev/null || :; fi
    # Bound shutdown as well as startup. Spring normally handles SIGTERM.
    remaining=10
    while [ "$remaining" -gt 0 ]; do
        alive=false
        if [ -n "$java_pid" ] && kill -0 "$java_pid" 2>/dev/null; then alive=true; fi
        if [ -n "$daemon_pid" ] && kill -0 "$daemon_pid" 2>/dev/null; then alive=true; fi
        if [ "$alive" = false ]; then break; fi
        sleep 1
        remaining=$((remaining - 1))
    done
    if [ -n "$java_pid" ]; then
        kill -KILL "$java_pid" 2>/dev/null || :
        wait "$java_pid" 2>/dev/null || :
    fi
    if [ -n "$daemon_pid" ]; then
        kill -KILL "$daemon_pid" 2>/dev/null || :
        wait "$daemon_pid" 2>/dev/null || :
    fi
    # Remove only named files in the private directory created by this process.
    if [ -n "$runtime_dir" ]; then
        rm -f "$runtime_dir/authkey" "$runtime_dir/tailscaled.sock"
        rmdir "$runtime_dir" 2>/dev/null || :
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[ -n "${TS_AUTHKEY:-}" ] || fail 'TS_AUTHKEY is missing; configure the Render runtime secret.'
[ -n "${DB_URL:-}" ] || fail 'DB_URL is missing; configure the MySQL JDBC URL.'
[ -n "${DB_USERNAME:-}" ] || fail 'DB_USERNAME is missing.'
[ -n "${DB_PASSWORD:-}" ] || fail 'DB_PASSWORD is missing.'

# One authoritative destination for both the readiness probe and JDBC.
db_host=${TS_DB_HOST:-100.94.202.58}
db_port=${TS_DB_PORT:-3306}
case "$DB_URL" in
    "jdbc:mysql://$db_host:$db_port/"*) ;;
    *) fail 'DB_URL must target TS_DB_HOST:TS_DB_PORT (defaults: laboratory Tailscale IP and port 3306).' ;;
esac

for command in tailscaled tailscale timeout nc java; do
    command -v "$command" >/dev/null 2>&1 || fail "Required executable is unavailable: $command"
done

runtime_dir=$(mktemp -d /tmp/rehab-tailscale.XXXXXX)
socket="$runtime_dir/tailscaled.sock"
# file: avoids exposing the key in the CLI argument list. The file is mode 600.
printf '%s' "$TS_AUTHKEY" > "$runtime_dir/authkey"
unset TS_AUTHKEY

log 'Starting Tailscale userspace daemon; SOCKS5 is loopback-only on port 1055.'
tailscaled --tun=userspace-networking --state=mem: --socket="$socket" \
    --socks5-server=127.0.0.1:1055 >/dev/null 2>&1 &
daemon_pid=$!

ready=false
attempt=0
while [ "$attempt" -lt 10 ]; do
    kill -0 "$daemon_pid" 2>/dev/null || fail 'tailscaled exited before becoming ready.'
    if timeout -k 1s 2s tailscale --socket="$socket" status --json >/dev/null 2>&1; then
        ready=true
        break
    fi
    attempt=$((attempt + 1))
    sleep 1
done
[ "$ready" = true ] || fail 'Tailscale local control socket was not ready within the startup deadline.'

log 'Authenticating Tailscale (maximum 50 seconds).'
if timeout -k 2s 50s tailscale --socket="$socket" up \
    --auth-key="file:$runtime_dir/authkey" --timeout=45s \
    --hostname="${TS_HOSTNAME:-rehabassist-render}" --accept-dns=false \
    --accept-routes=false >/dev/null 2>&1; then
    rm -f "$runtime_dir/authkey"
else
    fail 'Tailscale authentication failed or timed out; check key validity, device approval and tailnet policy in the admin console.'
fi

if ! timeout -k 1s 3s tailscale --socket="$socket" status --json 2>/dev/null \
    | grep -Eq '"BackendState"[[:space:]]*:[[:space:]]*"Running"'; then
    fail 'Tailscale did not reach the authenticated Running state.'
fi

# Probe the actual MySQL TCP port through the same SOCKS proxy, not ICMP.
# This proves routing/ACL/listener reachability, NOT database authentication.
reachable=false
attempt=0
while [ "$attempt" -lt 3 ]; do
    kill -0 "$daemon_pid" 2>/dev/null || fail 'tailscaled exited before database reachability check.'
    if timeout -k 1s 6s nc -z -w 5 -X 5 -x 127.0.0.1:1055 "$db_host" "$db_port" >/dev/null 2>&1; then
        reachable=true
        break
    fi
    attempt=$((attempt + 1))
    if [ "$attempt" -lt 3 ]; then sleep 2; fi
done
[ "$reachable" = true ] || fail 'Laboratory MySQL TCP port is unreachable through SOCKS5; check device online status, tailnet ACL, firewall and MySQL listener.'

log 'Tailscale authenticated and laboratory MySQL TCP port reachable; starting Spring Boot.'
# Do not set global JVM SOCKS properties: Google/Resend and HTTP keep their
# existing routes. Hikari forwards these properties only to Connector/J.
# PORT remains owned by server.port=${PORT:8080} in application.properties.
java -Xms32m -Xmx224m -Xss512k -XX:MaxMetaspaceSize=96m \
    -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m \
    -XX:+ExitOnOutOfMemoryError -jar /app/app.jar "$@" \
    --spring.datasource.hikari.data-source-properties.socksProxyHost=127.0.0.1 \
    --spring.datasource.hikari.data-source-properties.socksProxyPort=1055 \
    --spring.datasource.hikari.data-source-properties.connectTimeout=10000 \
    --spring.datasource.hikari.data-source-properties.socketTimeout=30000 &
java_pid=$!

# Supervise both children. A daemon crash must not leave an unreachable JVM
# running indefinitely. This checks local PIDs only, not repeated SQL queries.
while kill -0 "$java_pid" 2>/dev/null; do
    kill -0 "$daemon_pid" 2>/dev/null || fail 'tailscaled exited while Spring Boot was running; stopping the service.'
    sleep 1
done
exit_code=0
wait "$java_pid" || exit_code=$?
exit "$exit_code"
