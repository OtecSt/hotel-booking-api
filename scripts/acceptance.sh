#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

java_bin=java
if [[ -n "${JAVA_HOME:-}" ]]; then
    java_bin="$JAVA_HOME/bin/java"
fi
log_dir=$(mktemp -d)
pids=()
cleanup() {
    local code=$?
    if (( code != 0 )); then
        echo "Acceptance logs: $log_dir" >&2
        for log in "$log_dir"/*.log; do
            echo "=== $log ===" >&2
            tail -40 "$log" >&2
        done
    fi
    for ((index=${#pids[@]}-1; index>=0; index--)); do
        kill "${pids[index]}" 2>/dev/null || true
        wait "${pids[index]}" 2>/dev/null || true
    done
    if (( code == 0 )); then
        if [[ "${KEEP_ACCEPTANCE_LOGS:-0}" == 1 ]]; then
            echo "Acceptance logs: $log_dir"
        else
            rm -rf "$log_dir"
        fi
    fi
}
trap cleanup EXIT

start() {
    local module=$1
    shift
    "$java_bin" -jar "$module/target/$module-0.1.0-SNAPSHOT.jar" "$@" >"$log_dir/$module.log" 2>&1 &
    pids+=("$!")
}

await_status() {
    local url=$1 pattern=$2
    local attempt
    for ((attempt=0; attempt<60; attempt++)); do
        if [[ $(curl -fsS --max-time 2 "$url" 2>/dev/null || true) == *"$pattern"* ]]; then
            return
        fi
        sleep 1
    done
    echo "Timed out waiting for $url" >&2
    return 1
}

export HOTEL_BOOKING_JWT_SECRET
HOTEL_BOOKING_JWT_SECRET=$(openssl rand -hex 32)
export HOTEL_BOOKING_DEMO_ADMIN_PASSWORD
HOTEL_BOOKING_DEMO_ADMIN_PASSWORD=$(openssl rand -hex 16)
export EUREKA_URL=http://127.0.0.1:18761/eureka/
export GATEWAY_URL=http://127.0.0.1:18080
export HOTEL_FAULT_PROXY_URL=http://127.0.0.1:18083
export BOOKING_URL=http://127.0.0.1:18082
export HOTEL_URL=http://127.0.0.1:18081

start eureka-server --server.port=18761 --eureka.server.use-read-only-response-cache=false
await_status "http://127.0.0.1:18761/actuator/health" '"status":"UP"'
python3 scripts/hotel-fault-proxy.py 18083 "$HOTEL_URL" >"$log_dir/fault-proxy.log" 2>&1 &
pids+=("$!")
await_status "$HOTEL_FAULT_PROXY_URL/__test/status" '"dropped"'

start hotel-service --server.port=18081 --spring.profiles.active=demo \
    --eureka.instance.prefer-ip-address=true --eureka.instance.ip-address=127.0.0.1 \
    --eureka.instance.non-secure-port=18083
await_status "$HOTEL_URL/actuator/health" '"status":"UP"'
await_status "${EUREKA_URL}apps/HOTEL-SERVICE" '<status>UP</status>'

start booking-service --server.port=18082 --spring.profiles.active=demo \
    --app.release-retry-delay=PT1S \
    --eureka.instance.prefer-ip-address=true --eureka.instance.ip-address=127.0.0.1
await_status "$BOOKING_URL/actuator/health" '"status":"UP"'
await_status "${EUREKA_URL}apps/BOOKING-SERVICE" '<status>UP</status>'

start api-gateway --server.port=18080 --eureka.client.registry-fetch-interval-seconds=1
await_status "$GATEWAY_URL/actuator/health" '"status":"UP"'
python3 scripts/smoke-test.py
