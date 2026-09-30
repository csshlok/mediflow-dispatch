#!/usr/bin/env bash
# End-to-end check of the dispatch flow against a throwaway stack.
#
# Starts an isolated compose project (mediflow-e2e, own volumes, throwaway admin), runs the checks through the
# gateway on localhost:8080, then removes the project and its volumes. Your own stack and data are not touched,
# but it must not be running at the same time (ports 8080/3000/9090 and the loopback infra ports are shared).
#
# Needs: docker compose, curl, python 3 with the "websockets" package, and a filled-in .env (see .env.example).
# Usage: scripts/e2e/run.sh            (KEEP=1 leaves the stack running afterwards)
set -u
cd "$(dirname "$0")/../.."

PROJECT=mediflow-e2e
H=http://localhost:8080
WS=ws://localhost:8080/ws/notifications
HERE=scripts/e2e
export BOOTSTRAP_ADMIN_EMAIL="e2e-admin@mediflow.local"
export BOOTSTRAP_ADMIN_PASSWORD="e2e-$(python -c 'import secrets; print(secrets.token_hex(8))')"

PASS=0; FAIL=0
ok()     { echo "PASS: $1"; PASS=$((PASS+1)); }
bad()    { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
expect() { [ "$2" = "$3" ] && ok "$1 ($3)" || bad "$1 (expected $2, got $3)"; }
json()   { python -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }
code()   { curl -s -o /dev/null -w "%{http_code}" "$@"; }
login()  { curl -s -X POST $H/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$2\"}" | json 'd["token"]'; }
status_of() { curl -s $H/api/emergency/$1 -H "Authorization: Bearer $DISP" | json 'd["status"]'; }
wait_status() { for _ in $(seq 1 20); do [ "$(status_of $1)" = "$2" ] && break; sleep 1; done; status_of $1; }

cleanup() {
  if [ -n "${WS_PID:-}" ]; then
    kill "$WS_PID" 2>/dev/null
    wait "$WS_PID" 2>/dev/null
  fi
  rm -f "$WS_OUT"
  if [ "${KEEP:-0}" != "1" ]; then
    echo "Removing $PROJECT stack and volumes"
    docker compose -p $PROJECT down -v >/dev/null 2>&1
  fi
}
WS_OUT=$(mktemp)
trap cleanup EXIT

echo "Starting $PROJECT stack (first build takes several minutes)"
docker compose -p $PROJECT up -d --build >/dev/null 2>&1 || { echo "compose up failed"; exit 1; }
for _ in $(seq 1 180); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' $PROJECT-api-gateway-1 2>/dev/null)" = healthy ] && break
  sleep 2
done
for _ in $(seq 1 30); do [ -n "$(login "$BOOTSTRAP_ADMIN_EMAIL" "$BOOTSTRAP_ADMIN_PASSWORD")" ] && break; sleep 2; done

# --- Accounts: registration is admin-only ---
ADMIN=$(login "$BOOTSTRAP_ADMIN_EMAIL" "$BOOTSTRAP_ADMIN_PASSWORD")
[ -n "$ADMIN" ] && ok "bootstrap admin can log in" || { bad "bootstrap admin login"; exit 1; }
expect "anonymous register is rejected" 401 $(code -X POST $H/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"x","email":"evil@x.io","password":"password123","role":"ADMIN"}')
for r in DISPATCHER PARAMEDIC; do
  expect "admin registers $r" 201 $(code -X POST $H/api/auth/register -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
    -d "{\"name\":\"$r\",\"email\":\"$r@e2e.io\",\"password\":\"password123\",\"role\":\"$r\"}")
done
DISP=$(login DISPATCHER@e2e.io password123)
PARA=$(login PARAMEDIC@e2e.io password123)
expect "dispatcher cannot register users" 403 $(code -X POST $H/api/auth/register -H 'Content-Type: application/json' -H "Authorization: Bearer $DISP" \
  -d '{"name":"y","email":"y@e2e.io","password":"password123","role":"ADMIN"}')
TAMPERED="${DISP%?}x"
expect "tampered token is rejected" 401 $(code $H/api/cases -H "Authorization: Bearer $TAMPERED")

# --- Network exposure ---
expect "ambulance-service port 8083 is not published" 000 $(code --max-time 3 http://localhost:8083/ambulances)

# --- Resources: two ambulances, no hospital yet ---
AMB1=$(curl -s -X POST $H/api/ambulances -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: amb-1" \
  -d '{"registrationNumber":"E2E-1","capabilities":"ALS","crewInfo":"2"}' | json 'd["id"]')
AMB2=$(curl -s -X POST $H/api/ambulances -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: amb-2" \
  -d '{"registrationNumber":"E2E-2","capabilities":"BLS","crewInfo":"2"}' | json 'd["id"]')
[ -n "$AMB1" ] && [ -n "$AMB2" ] && ok "two ambulances registered" || bad "ambulance registration"
expect "dispatcher can list ambulances" 200 $(code $H/api/ambulances -H "Authorization: Bearer $DISP")
echo "... waiting 12s for the location simulator to place the ambulances"; sleep 12

# --- Emergency intake ---
expect "emergency without severity is rejected" 400 $(code -X POST $H/api/emergency -H 'Content-Type: application/json' -H "Authorization: Bearer $DISP" \
  -H "Idempotency-Key: bad-1" -d '{"patientId":"P1","latitude":28.61,"longitude":77.2}')
BODY='{"patientId":"PAT-E2E","severity":"CRITICAL","latitude":28.62,"longitude":77.21}'
R1=$(curl -s -X POST $H/api/emergency -H 'Content-Type: application/json' -H "Authorization: Bearer $DISP" -H "Idempotency-Key: e2e-1" -d "$BODY")
R2=$(curl -s -X POST $H/api/emergency -H 'Content-Type: application/json' -H "Authorization: Bearer $DISP" -H "Idempotency-Key: e2e-1" -d "$BODY")
EID=$(echo "$R1" | grep -oE '[0-9a-f-]{36}$')
[ -n "$EID" ] && ok "emergency accepted ($EID)" || bad "emergency submit: $R1"
[ "$R1" = "$R2" ] && ok "retry with the same Idempotency-Key returns the same emergency" || bad "idempotent retry differs"
expect "new emergency is PENDING_MATCH" PENDING_MATCH "$(status_of $EID)"

# --- No hospital: the dispatch fails, compensates, and is retried ---
echo "... waiting 20s: dispatch should fail (no hospital) and release the ambulance"; sleep 20
expect "no ambulance left reserved after the failed attempt" 0 "$(curl -s "$H/api/ambulances?status=RESERVED" -H "Authorization: Bearer $ADMIN" | json 'len(d)')"

HID=$(curl -s -X POST $H/api/hospitals -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: hosp-1" \
  -d '{"hospitalName":"E2E General","availableBeds":5,"latitude":28.63,"longitude":77.22}' | json 'd["id"]')
[ -n "$HID" ] && ok "hospital registered" || bad "hospital registration"

echo "... waiting up to 150s for the retried dispatch to create a case"
CASE_AMB=""
for _ in $(seq 1 30); do
  CASE_AMB=$(curl -s $H/api/cases -H "Authorization: Bearer $DISP" | json "next((c['ambulanceId'] for c in d if c['emergencyId']=='$EID'), '')")
  [ -n "$CASE_AMB" ] && break; sleep 5
done
[ -n "$CASE_AMB" ] && ok "case created after retry (ambulance $CASE_AMB)" || bad "no case for emergency $EID"
OTHER_AMB=$([ "$CASE_AMB" = "$AMB1" ] && echo "$AMB2" || echo "$AMB1")

expect "emergency is DISPATCHED" DISPATCHED "$(wait_status $EID DISPATCHED)"
expect "saga is COMPLETED" COMPLETED "$(curl -s $H/api/matching/sagas/$EID -H "Authorization: Bearer $ADMIN" | json 'd["state"]')"
expect "dispatcher cannot read sagas" 403 $(code $H/api/matching/sagas/$EID -H "Authorization: Bearer $DISP")
expect "assigned ambulance is RESERVED for this emergency" "RESERVED $EID" \
  "$(curl -s $H/api/ambulances -H "Authorization: Bearer $ADMIN" | json "[a['status']+' '+str(a['assignedEmergencyId']) for a in d if a['id']=='$CASE_AMB'][0]")"
expect "exactly one bed taken" 4 "$(curl -s "$H/api/hospitals?minBeds=0" -H "Authorization: Bearer $ADMIN" | json "[h['availableBeds'] for h in d if h['id']=='$HID'][0]")"

# --- Paramedic is bound to one ambulance ---
PICKUP=$H/api/ambulances/$CASE_AMB/pickup/$EID
expect "unassigned paramedic cannot record a pickup" 403 $(code -X POST $PICKUP -H "Authorization: Bearer $PARA")
PARA_ID=$(curl -s $H/api/auth/users -H "Authorization: Bearer $ADMIN" | json "[u['id'] for u in d if u['email']=='PARAMEDIC@e2e.io'][0]")
expect "admin assigns paramedic to the other ambulance" 200 $(code -X PUT $H/api/auth/users/$PARA_ID/ambulance -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN" -d "{\"ambulanceId\":\"$OTHER_AMB\"}")
PARA=$(login PARAMEDIC@e2e.io password123)
expect "paramedic of another ambulance cannot record the pickup" 403 $(code -X POST $PICKUP -H "Authorization: Bearer $PARA")
code -X PUT $H/api/auth/users/$PARA_ID/ambulance -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
  -d "{\"ambulanceId\":\"$CASE_AMB\"}" >/dev/null
PARA=$(login PARAMEDIC@e2e.io password123)
expect "paramedic cannot call the internal reserve endpoint" 403 $(code -X POST $H/api/ambulances/$CASE_AMB/reserve -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $PARA" -d "{\"emergencyId\":\"$EID\"}")
expect "pickup for a different emergency is refused" 409 $(code -X POST $H/api/ambulances/$CASE_AMB/pickup/00000000-0000-0000-0000-000000000000 -H "Authorization: Bearer $PARA")
expect "delivery before pickup is refused" 409 $(code -X POST $H/api/ambulances/$CASE_AMB/deliver/$EID/$HID -H "Authorization: Bearer $PARA")

# --- Live notifications over the gateway WebSocket ---
python $HERE/ws_listen.py "$WS" "$WS_OUT" 1 >/dev/null 2>&1
grep -q "REJECTED 401" "$WS_OUT" && ok "WebSocket without a token is rejected (401)" || bad "unauthenticated WebSocket: $(cat "$WS_OUT")"
: > "$WS_OUT"
python $HERE/ws_listen.py "$WS?access_token=$DISP" "$WS_OUT" 45 >/dev/null 2>&1 &
WS_PID=$!
for _ in $(seq 1 20); do grep -q CONNECTED "$WS_OUT" && break; sleep 0.5; done
grep -q CONNECTED "$WS_OUT" && ok "dispatcher connects to the notification stream" || bad "WebSocket connect: $(cat "$WS_OUT")"

# --- Pickup, delivery, discharge ---
expect "paramedic records pickup" 200 $(code -X POST $PICKUP -H "Authorization: Bearer $PARA")
expect "repeated pickup tap is harmless" 200 $(code -X POST $PICKUP -H "Authorization: Bearer $PARA")
expect "emergency is PATIENT_PICKED_UP" PATIENT_PICKED_UP "$(wait_status $EID PATIENT_PICKED_UP)"
expect "paramedic records delivery" 200 $(code -X POST $H/api/ambulances/$CASE_AMB/deliver/$EID/$HID -H "Authorization: Bearer $PARA")
expect "emergency is DELIVERED" DELIVERED "$(wait_status $EID DELIVERED)"
expect "ambulance is AVAILABLE after delivery" "AVAILABLE None" \
  "$(curl -s $H/api/ambulances -H "Authorization: Bearer $ADMIN" | json "[a['status']+' '+str(a['assignedEmergencyId']) for a in d if a['id']=='$CASE_AMB'][0]")"

for _ in $(seq 1 20); do grep -q "arrived at Hospital" "$WS_OUT" && break; sleep 1; done
grep -q "secured the patient" "$WS_OUT" && ok "pickup notification pushed over WebSocket" || bad "no pickup notification on WebSocket"
grep -q "arrived at Hospital" "$WS_OUT" && ok "delivery notification pushed over WebSocket" || bad "no delivery notification on WebSocket"

expect "discharge returns the bed" 200 $(code -X POST $H/api/hospitals/$HID/discharge/$EID -H "Authorization: Bearer $ADMIN")
expect "repeated discharge is harmless" 200 $(code -X POST $H/api/hospitals/$HID/discharge/$EID -H "Authorization: Bearer $ADMIN")
expect "hospital back to 5 beds" 5 "$(curl -s "$H/api/hospitals?minBeds=0" -H "Authorization: Bearer $ADMIN" | json "[h['availableBeds'] for h in d if h['id']=='$HID'][0]")"

sleep 8
expect "late dispatch event does not re-reserve the ambulance" AVAILABLE \
  "$(curl -s $H/api/ambulances -H "Authorization: Bearer $ADMIN" | json "[a['status'] for a in d if a['id']=='$CASE_AMB'][0]")"

# --- Operations ---
expect "admin can re-drive the dispatch DLQ (empty)" 0 "$(curl -s -X POST $H/api/matching/dlq/redrive -H "Authorization: Bearer $ADMIN" | json 'd["redriven"]')"
expect "dispatcher cannot re-drive the DLQ" 403 $(code -X POST $H/api/matching/dlq/redrive -H "Authorization: Bearer $DISP")
expect "Prometheus scrapes all 9 services" 9 "$(curl -s http://localhost:9090/api/v1/targets | json "sum(1 for t in d['data']['activeTargets'] if t['health']=='up')")"

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
