#!/usr/bin/env bash

BASE_URL="http://localhost:8080"

read -rp "Username: " USERNAME
read -rsp "Password: " PASSWORD
echo

if ! command -v jq >/dev/null 2>&1; then
    echo "Ошибка: установи jq (sudo pacman -S jq)"
    exit 1
fi

TMP_FILE=$(mktemp)
trap 'rm -f "$TMP_FILE"' EXIT

PASS=0
FAIL=0

check() {
    local NAME="$1"
    local URL="$2"
    local EXPECTED="$3"
    local TOKEN="${4:-}"

    if [ -n "$TOKEN" ]; then
        CODE=$(curl -sS -o "$TMP_FILE" -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" \
            "$URL")
    else
        CODE=$(curl -sS -o "$TMP_FILE" -w "%{http_code}" "$URL")
    fi

    if [ "$CODE" = "$EXPECTED" ]; then
        echo "[OK]   $NAME ($CODE)"
        ((PASS+=1))
    else
        echo "[FAIL] $NAME (ожидалось $EXPECTED, получено $CODE)"
        cat "$TMP_FILE"
        echo
        ((FAIL+=1))
    fi
}

echo
echo "========== SWAGGER =========="

check "Auth Swagger" "$BASE_URL/v3/api-docs/auth" "200"
check "Product Swagger" "$BASE_URL/v3/api-docs/product" "200"
check "Compatibility Swagger" "$BASE_URL/v3/api-docs/compatibility" "200"
check "Order Swagger" "$BASE_URL/v3/api-docs/order" "200"

echo
echo "========== AUTHENTICATION =========="

LOGIN_RESPONSE=$(curl -sS -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg username "$USERNAME" \
        --arg password "$PASSWORD" \
        '{username: $username, password: $password}')")

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token // empty')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "[FAIL] Не удалось получить JWT"
    echo "$LOGIN_RESPONSE" | jq . 2>/dev/null || echo "$LOGIN_RESPONSE"
    exit 1
fi

echo "[OK]   JWT получен"

echo
echo "========== PROTECTED API =========="

check "Auth: current user" "$BASE_URL/users/me" "200" "$TOKEN"
check "Product: components" "$BASE_URL/components" "200" "$TOKEN"
check "Compatibility: rules" "$BASE_URL/rules" "200" "$TOKEN"
check "Order: orders" "$BASE_URL/orders" "200" "$TOKEN"

echo
echo "========== RESULT =========="
echo "Успешно: $PASS"
echo "Ошибок:  $FAIL"

if [ "$FAIL" -eq 0 ]; then
    echo "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ"
    exit 0
else
    echo "ЕСТЬ ПРОБЛЕМЫ — СМОТРИ ОШИБКИ ВЫШЕ"
    exit 1
fi
