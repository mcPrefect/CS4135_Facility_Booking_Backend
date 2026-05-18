#!/bin/bash
# quick test to check the main booking flow works end to end

BASE="http://localhost:8080"
PASS=0
FAIL=0

green() { echo -e "\033[32m✓ $1\033[0m"; }
red()   { echo -e "\033[31m✗ $1 (got: $2)\033[0m"; }

check() {
    local label=$1
    local value=$2
    local expected=$3
    if echo "$value" | grep -q "$expected"; then
        green "$label"
        ((PASS++))
    else
        red "$label" "$value"
        ((FAIL++))
    fi
}

# purge leftover nlp events from previous runs so they dont cause booking conflicts
curl -s -X DELETE "http://guest:guest@localhost:15672/api/queues/%2F/booking.nlp.intent.queue/contents" > /dev/null

# register an admin and a student with unique emails so it doesnt clash
ADMIN_EMAIL="testadmin_$$@ul.ie"
STUDENT_EMAIL="teststudent_$$@ul.ie"

# unique facility name and date per run to avoid conflicts
FACILITY_NAME="Test Hall $$"
BOOKING_DATE=$(date -d "+30 days" +"%B %d" 2>/dev/null || date -v+30d +"%B %d")

echo "registering users..."

REGISTER=$(curl -s -X POST $BASE/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"password123\",\"role\":\"ADMIN\"}")
check "admin registration" "$REGISTER" "User registered successfully"

REGISTER=$(curl -s -X POST $BASE/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"password123\",\"role\":\"STUDENT\"}")
check "student registration" "$REGISTER" "User registered successfully"

echo "logging in..."

LOGIN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"password123\"}")
ADMIN_TOKEN=$(echo "$LOGIN" | jq -r .token)
check "admin login" "$ADMIN_TOKEN" "eyJ"

LOGIN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"password123\"}")
STUDENT_TOKEN=$(echo "$LOGIN" | jq -r .token)
STUDENT_ID=$(echo "$LOGIN" | jq -r .userId)
check "student login" "$STUDENT_TOKEN" "eyJ"

echo "creating facility..."

FACILITY=$(curl -s -X POST $BASE/api/v1/facilities \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"$FACILITY_NAME\",\"type\":\"LECTURE_ROOM\",\"capacity\":30,\"building\":\"Main Block\"}")
check "create facility" "$FACILITY" "$FACILITY_NAME"

echo "sending nlp query..."

NLP=$(curl -s -X POST $BASE/api/nlp/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"rawText\": \"Book the $FACILITY_NAME on $BOOKING_DATE at 10am for 1 hour\"}")
check "nlp query resolved" "$NLP" "RESOLVED"
check "facility extracted" "$NLP" "$FACILITY_NAME"
check "time extracted" "$NLP" "10:00"

echo "waiting for booking to come through rabbitmq..."
sleep 5

BOOKINGS=$(curl -s $BASE/api/v1/bookings \
  -H "Authorization: Bearer $STUDENT_TOKEN")
BOOKING_ID=$(echo "$BOOKINGS" | jq -r '[.[] | select(.status == "PENDING")][0].bookingId')
check "booking created" "$BOOKINGS" "NLP Booking"
check "booking is pending" "$BOOKINGS" "PENDING"

PENDING=$(curl -s $BASE/api/v1/approvals/pending \
  -H "Authorization: Bearer $ADMIN_TOKEN")
check "approval task exists" "$PENDING" "$BOOKING_ID"

echo "approving booking..."

APPROVAL=$(curl -s -X PATCH $BASE/api/v1/approvals/$BOOKING_ID/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"reason":"looks good"}')
check "approval went through" "$APPROVAL" "APPROVED"

sleep 2

BOOKING=$(curl -s $BASE/api/v1/bookings \
  -H "Authorization: Bearer $STUDENT_TOKEN")
check "booking updated to approved" "$BOOKING" "APPROVED"

NOTIFS=$(curl -s $BASE/api/v1/notifications/$STUDENT_ID \
  -H "Authorization: Bearer $STUDENT_TOKEN")
check "got pending notification" "$NOTIFS" "BOOKING_PENDING_APPROVAL"
check "got confirmed notification" "$NOTIFS" "BOOKING_CONFIRMED"

echo ""
echo "$PASS passed, $FAIL failed"
