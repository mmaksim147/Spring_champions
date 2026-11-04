
#!/usr/bin/env bash

set -uo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

GATEWAY="http://localhost:8080"
EUREKA="http://localhost:8761/eureka"
CONFIG="http://localhost:8888"

AUTH="http://localhost:8081"
PRODUCT="http://localhost:8082"
ORDER="http://localhost:8083"
COMPATIBILITY="http://localhost:8084"

PASS=0
FAIL=0
WARN=0
TOTAL=0

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

BODY="$TMP_DIR/body"
HEADERS="$TMP_DIR/headers"
ERROR="$TMP_DIR/error"

HTTP_CODE="000"
TOKEN=""

section() {
    echo
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

ok() {
    echo -e "${GREEN}[PASS]${NC} $1"
    PASS=$((PASS + 1))
    TOTAL=$((TOTAL + 1))
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    FAIL=$((FAIL + 1))
    TOTAL=$((TOTAL + 1))
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    WARN=$((WARN + 1))
    TOTAL=$((TOTAL + 1))
}

request() {
    local method="$1"
    local url="$2"
    local data="${3:-}"
    local accept="${4:-application/json}"

    : > "$BODY"
    : > "$HEADERS"
    : > "$ERROR"

    local args=(
        -sS
        --connect-timeout 3
        --max-time 15
        -X "$method"
        "$url"
        -H "Accept: $accept"
        -D "$HEADERS"
        -o "$BODY"
        -w "%{http_code}"
    )

    if [ -n "$data" ]; then
        args+=(
            -H "Content-Type: application/json"
            -d "$data"
        )
    fi

    if [ -n "$TOKEN" ]; then
        args+=(-H "Authorization: Bearer $TOKEN")
    fi

    HTTP_CODE=$(curl "${args[@]}" 2>"$ERROR") || {
        HTTP_CODE="${HTTP_CODE:-000}"
    }

    if [ -s "$ERROR" ]; then
        return 1
    fi

    return 0
}

check_http() {
    local name="$1"
    local method="$2"
    local url="$3"
    local expected="$4"
    local data="${5:-}"
    local accept="${6:-application/json}"

    request "$method" "$url" "$data" "$accept"

    if [ "$HTTP_CODE" = "$expected" ]; then
        ok "$name -> HTTP $HTTP_CODE"
    else
        fail "$name: ожидался HTTP $expected, получен $HTTP_CODE"

        if [ -s "$BODY" ]; then
            echo "Ответ сервера:"
            cat "$BODY"
            echo
        fi

        if [ -s "$ERROR" ]; then
            cat "$ERROR"
        fi
    fi
}

check_eureka() {
    local service="$1"
    local url="$EUREKA/apps/$service"

    request GET "$url" "" "application/json"

    if [ "$HTTP_CODE" != "200" ]; then
        fail "Eureka: $service -> HTTP $HTTP_CODE"
        cat "$BODY"
        return
    fi

    local status
    status=$(jq -r '.application.instance[]?.status // empty' "$BODY" 2>/dev/null | head -n 1)

    if [ "$status" = "UP" ]; then
        ok "Eureka: $service зарегистрирован и UP"
    else
        fail "Eureka: $service зарегистрирован, но статус ${status:-не определён}"
        cat "$BODY"
        echo
    fi
}

check_swagger() {
    local path="$1"

    request GET "$GATEWAY$path"

    if [ "$HTTP_CODE" != "200" ]; then
        fail "Swagger $path -> HTTP $HTTP_CODE"
        cat "$BODY"
        echo
        return
    fi

    if jq -e '.openapi and .paths' "$BODY" >/dev/null 2>&1; then
        ok "Swagger $path"
    else
        fail "Swagger $path: некорректный OpenAPI JSON"
        cat "$BODY"
        echo
    fi
}

check_protected() {
    local name="$1"
    local path="$2"

    local saved_token="$TOKEN"
    TOKEN=""

    request GET "$GATEWAY$path"

    if [ "$HTTP_CODE" = "401" ]; then
        ok "$name без JWT -> HTTP 401"
    else
        fail "$name без JWT: ожидался HTTP 401, получен $HTTP_CODE"
    fi

    TOKEN="$saved_token"
}

echo
echo -e "${CYAN}==========================================${NC}"
echo -e "${CYAN}       BACKEND COMPREHENSIVE TEST${NC}"
echo -e "${CYAN}==========================================${NC}"
echo
echo "Проверка проекта Pract9"
echo "Gateway: $GATEWAY"
echo

for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo -e "${RED}Не найдена программа: $cmd${NC}"
        echo "Установи: sudo pacman -S curl jq"
        exit 1
    fi
done

read -r -p "Username: " USERNAME
read -r -s -p "Password: " PASSWORD
echo

if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
    echo -e "${RED}Логин и пароль не должны быть пустыми.${NC}"
    exit 1
fi

section "[1] INFRASTRUCTURE"

check_http "API Gateway" GET "$GATEWAY/actuator/health" 200
check_http "Config Server" GET "$CONFIG/actuator/health" 200

request GET "$EUREKA/apps" "" "application/json"

if [ "$HTTP_CODE" = "200" ] && jq -e '.applications' "$BODY" >/dev/null 2>&1; then
    ok "Eureka Server -> HTTP 200"
else
    fail "Eureka Server: HTTP $HTTP_CODE или некорректный JSON"
    cat "$BODY"
    echo
fi

section "[2] EUREKA REGISTRATION"

check_eureka "AUTH-SERVICE"
check_eureka "PRODUCT-SERVICE"
check_eureka "COMPATIBILITY-SERVICE"
check_eureka "ORDER-SERVICE"
check_eureka "API-GATEWAY"

section "[3] SERVICE HEALTH"

check_http "Auth Service health" GET "$AUTH/actuator/health" 200
check_http "Product Service health" GET "$PRODUCT/actuator/health" 200
check_http "Compatibility Service health" GET "$COMPATIBILITY/actuator/health" 200
check_http "Order Service health" GET "$ORDER/actuator/health" 200

section "[4] SWAGGER DOCUMENTATION"

check_swagger "/v3/api-docs/auth"
check_swagger "/v3/api-docs/product"
check_swagger "/v3/api-docs/compatibility"
check_swagger "/v3/api-docs/order"

section "[5] AUTHENTICATION"

LOGIN_DATA=$(jq -n \
    --arg username "$USERNAME" \
    --arg password "$PASSWORD" \
    '{username: $username, password: $password}')

TOKEN=""

request POST "$GATEWAY/api/auth/login" "$LOGIN_DATA"

if [ "$HTTP_CODE" = "200" ]; then
    TOKEN=$(jq -r '.token // .accessToken // .jwt // empty' "$BODY" 2>/dev/null)

    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        ok "Авторизация: JWT получен"
    else
        fail "Авторизация: ответ 200, но JWT не найден"
        cat "$BODY"
        echo
        echo "Проверь название поля токена в ответе Auth Service."
        exit 1
    fi
else
    fail "Авторизация не удалась: HTTP $HTTP_CODE"
    cat "$BODY"
    echo
    echo "Проверь логин, пароль, Auth Service и маршрут Gateway."
    exit 1
fi

section "[6] SECURITY TESTS"

check_protected "Product API" "/api/components"
check_protected "Compatibility API" "/rules"
check_protected "Order API" "/api/orders"

SAVED_TOKEN="$TOKEN"
TOKEN="invalid.jwt.token"

request GET "$GATEWAY/api/components"

if [ "$HTTP_CODE" = "401" ]; then
    ok "Неверный JWT -> HTTP 401"
else
    fail "Неверный JWT: ожидался HTTP 401, получен $HTTP_CODE"
fi

TOKEN="$SAVED_TOKEN"

check_http "Текущий пользователь" GET "$GATEWAY/users/me" 200

section "[7] PRODUCT SERVICE"

check_http "Получение компонентов" GET "$GATEWAY/api/components" 200
check_http "Получение категорий" GET "$GATEWAY/api/categories" 200
check_http "Получение сборок" GET "$GATEWAY/api/builds" 200

section "[8] COMPATIBILITY SERVICE"

check_http "Получение правил совместимости" GET "$GATEWAY/rules" 200

section "[9] ORDER SERVICE"

check_http "Получение заказов" GET "$GATEWAY/api/orders" 200

section "[10] ADMIN SECURITY"

check_http "Доступ администратора к /admin/users" GET "$GATEWAY/admin/users" 200

section "[11] FINAL REPORT"

echo
echo "Всего проверок: $TOTAL"
echo -e "Успешно:        ${GREEN}$PASS${NC}"
echo -e "Ошибок:         ${RED}$FAIL${NC}"
echo -e "Предупреждений: ${YELLOW}$WARN${NC}"
echo

if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN}ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ${NC}"
    exit 0
else
    echo -e "${RED}ОБНАРУЖЕНЫ ОШИБКИ. ПРОВЕРЬ ВЫВОД ВЫШЕ.${NC}"
    exit 1
fi
