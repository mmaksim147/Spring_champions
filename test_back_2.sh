#!/usr/bin/env bash
# =============================================================================
#  test_back.sh — E2E-тесты микросервисного бэкенда PC-Builder
#  Spring Cloud: Gateway(8080) · Eureka(8761) · Config(8888) · Auth(8081)
#                Product(8082) · Order(8083) · Compatibility(8084)
#
#  Зависимости: bash 3.2+, curl, jq   (openssl и sha256sum — опционально)
#
#  Использование:
#     ./test_back.sh                 # все проверки через Gateway
#     ./test_back.sh --direct        # бизнес-тесты напрямую в сервисы (в обход Gateway)
#     ./test_back.sh -v              # показывать тела ответов
#     ./test_back.sh --no-color
#
#  Переменные окружения (значения по умолчанию в скобках):
#     GATEWAY_URL (http://localhost:8080)   EUREKA_URL (http://localhost:8761)
#     CONFIG_URL  (http://localhost:8888)   AUTH_URL   (http://localhost:8081)
#     PRODUCT_URL (http://localhost:8082)   ORDER_URL  (http://localhost:8083)
#     COMPAT_URL  (http://localhost:8084)   ADMIN_USER (admin)  ADMIN_PASS (admin123)
#     TIMEOUT     (15, сек.)
#
#  Статусы:  ✔ PASS   ✘ FAIL (баг)   ⚠ WARN (недочёт/риск)   ⊘ SKIP (заблокировано)
#  Код возврата: 0 — нет FAIL, 1 — есть FAIL, 2 — не хватает зависимостей.
# =============================================================================
set -o pipefail

# ─────────────────────────────── Настройки ───────────────────────────────────
GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
EUREKA_URL=${EUREKA_URL:-http://localhost:8761}
CONFIG_URL=${CONFIG_URL:-http://localhost:8888}
AUTH_URL=${AUTH_URL:-http://localhost:8081}
PRODUCT_URL=${PRODUCT_URL:-http://localhost:8082}
ORDER_URL=${ORDER_URL:-http://localhost:8083}
COMPAT_URL=${COMPAT_URL:-http://localhost:8084}
ADMIN_USER=${ADMIN_USER:-admin}
ADMIN_PASS=${ADMIN_PASS:-admin123}
TIMEOUT=${TIMEOUT:-15}
DIRECT=0; VERBOSE=0; USE_COLOR=1

usage() { sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; }
for arg in "$@"; do
  case $arg in
    --direct)     DIRECT=1;;
    -v|--verbose) VERBOSE=1;;
    --no-color)   USE_COLOR=0;;
    -h|--help)    usage; exit 0;;
    *) echo "Неизвестный аргумент: $arg (см. --help)"; exit 2;;
  esac
done
GATEWAY_URL=${GATEWAY_URL%/}; EUREKA_URL=${EUREKA_URL%/}; CONFIG_URL=${CONFIG_URL%/}

if [[ -t 1 && -z ${NO_COLOR:-} && $USE_COLOR == 1 ]]; then
  R=$'\033[31m'; G=$'\033[32m'; Y=$'\033[33m'; B=$'\033[34m'; C=$'\033[36m'
  D=$'\033[2m';  BOLD=$'\033[1m'; N=$'\033[0m'
else
  R=; G=; Y=; B=; C=; D=; BOLD=; N=
fi

for dep in curl jq; do
  command -v "$dep" >/dev/null 2>&1 || { echo "Не найден «$dep». Установите его и повторите."; exit 2; }
done
HAVE_OPENSSL=0; command -v openssl >/dev/null 2>&1 && HAVE_OPENSSL=1

# ─────────────────────────────── Состояние ───────────────────────────────────
PASS=0; FAIL=0; WARN=0; SKIP=0
FAIL_LIST=(); WARN_LIST=(); ROOT_CAUSES=""
CUR_SECTION=""
USER_TOKEN=""; ADMIN_TOKEN=""; CONTROL_TOKEN=""; USER_ID=""
GW_SECRET=""; AU_SECRET=""
GW_DOWN=0; JWT_BLOCKED=0; JWT_MISMATCH=0; NOTE_CONTROL_SHOWN=0; CLEANED=0
CLEANUP=()
E2E_ID="$(date +%s)$RANDOM"
UID_N=0
USER_NAME="e2e_$E2E_ID"; USER_EMAIL="$USER_NAME@example.com"; USER_PASS='Passw0rd!123'
C_CPU=""; C_RAM=""; C_MB=""; C_MB2=""; C_CASE=""
CAT_CPU=""; CAT_MB=""; CAT_RAM=""; CAT_CASE=""

TMP=$(mktemp -d 2>/dev/null || mktemp -d -t e2e)
trap 'cleanup_all; rm -rf "$TMP"' EXIT

# ─────────────────────────────── Вывод ───────────────────────────────────────
section() { CUR_SECTION=$1; printf '\n%s━━ %s ━━%s\n' "$BOLD$B" "$1" "$N"; }
pass()  { PASS=$((PASS+1)); printf '  %s✔ PASS%s %s %s%s%s\n' "$G" "$N" "$1" "$D" "${2-}" "$N"; }
fail()  { FAIL=$((FAIL+1)); FAIL_LIST+=("[$CUR_SECTION] $1${2:+ — $2}")
          printf '  %s✘ FAIL%s %s\n' "$R" "$N" "$1"; [[ -n ${2-} ]] && printf '      %s%s%s\n' "$R" "$2" "$N"; }
warn()  { WARN=$((WARN+1)); WARN_LIST+=("[$CUR_SECTION] $1${2:+ — $2}")
          printf '  %s⚠ WARN%s %s\n' "$Y" "$N" "$1"; [[ -n ${2-} ]] && printf '      %s%s%s\n' "$Y" "$2" "$N"; }
skip()  { SKIP=$((SKIP+1)); printf '  %s⊘ SKIP%s %s %s(%s)%s\n' "$D" "$N" "$1" "$D" "${2-}" "$N"; }
info()  { printf '  %sℹ %s%s\n' "$C" "$*" "$N"; }
hint()  { printf '      %s↳ %s%s\n' "$Y" "$*" "$N"; }
ref()   { printf '      %s📍 %s%s\n' "$C" "$*" "$N"; }
root_cause() { # ключ текст — выводится в итоговом отчёте один раз
  case $ROOT_CAUSES in *"<<$1>>"*) return;; esac
  ROOT_CAUSES+="<<$1>>$2"$'\n'
}
snippet() { printf '%s' "$HTTP_BODY" | tr '\n\r' '  ' | cut -c1-300; }

# ─────────────────────────────── HTTP-слой ───────────────────────────────────
HTTP_CODE=000; HTTP_BODY=""; HTTP_MS=0; HTTP_URL=""; HTTP_METHOD=""; HTTP_PATH=""; CURL_ERR=""
AUTH_VAL=""; SKIP_REASON=""
SVC=""; ROUTE=""; CTRL=""; FEIGN=""

# Маппинг /api/... → прямой URL сервиса (режим --direct). Для compatibility
# префикс /api/compatibility убирается целиком — так, как задумано контроллерами.
resolve_url() {
  local p=$1
  case $p in http://*|https://*) printf '%s' "$p"; return;; esac
  if (( DIRECT )); then
    case $p in
      /api/auth/*|/api/admin*|/api/users*) printf '%s%s' "$AUTH_URL" "${p#/api}";;
      /api/products*|/api/categories*|/api/builds*|/api/components*) printf '%s%s' "$PRODUCT_URL" "${p#/api}";;
      /api/compatibility*) printf '%s%s' "$COMPAT_URL" "${p#/api/compatibility}";;
      /api/orders*|/api/order-items*|/api/status*) printf '%s%s' "$ORDER_URL" "${p#/api}";;
      *) printf '%s%s' "$GATEWAY_URL" "$p";;
    esac
  else
    printf '%s%s' "$GATEWAY_URL" "$p"
  fi
}

# http МЕТОД ПУТЬ [ТЕЛО] [ТОКЕН | RAW:<значение заголовка Authorization>]
http() {
  local method=$1 path=$2 data=${3-} auth=${4-} out rc t
  HTTP_METHOD=$method; HTTP_PATH=$path; HTTP_URL=$(resolve_url "$path")
  local args=(-sS -m "$TIMEOUT" -o "$TMP/body" -w '%{http_code}|%{time_total}' -X "$method"
              -H "Accept: ${HTTP_ACCEPT:-application/json}")
  if [[ -n $auth ]]; then
    if [[ $auth == RAW:* ]]; then args+=(-H "Authorization: ${auth#RAW:}")
    else args+=(-H "Authorization: Bearer $auth"); fi
  fi
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' --data-binary "$data")
  args+=("$HTTP_URL")
  : > "$TMP/body"
  out=$(curl "${args[@]}" 2>"$TMP/err"); rc=$?
  CURL_ERR=$(tr '\n' ' ' < "$TMP/err" | sed 's/^curl: //')
  HTTP_CODE=${out%%|*}; t=${out##*|}
  { [[ $rc -ne 0 ]] || [[ ! $HTTP_CODE =~ ^[0-9]{3}$ ]]; } && HTTP_CODE=000
  HTTP_MS=$(awk -v t="${t:-0}" 'BEGIN{printf "%d", t*1000}' 2>/dev/null)
  HTTP_BODY=$(cat "$TMP/body")
}

jqb() { printf '%s' "$HTTP_BODY" | jq -r "$@" 2>/dev/null | tr -d '\r'; }       # значение из ответа
jqt() { printf '%s' "$HTTP_BODY" | jq -e "$1" >/dev/null 2>&1; }                # истинно ли условие

# Токены: @user / @admin / @control, RAW:..., либо готовый токен.
prepare_auth() {
  local a=${1-} path=${2%%\?*} tok
  AUTH_VAL=""; SKIP_REASON=""
  case $a in
    @user|@admin)
      if [[ $a == @user ]]; then tok=$USER_TOKEN; else tok=$ADMIN_TOKEN; fi
      if [[ -z $tok ]]; then SKIP_REASON="нет токена $a: регистрация/логин не удались"; return 1; fi
      if (( JWT_BLOCKED && ! DIRECT )); then
        case $path in /api/users*|/api/admin*)
          SKIP_REASON="Gateway отвергает токены auth-service (несовпадение jwt.secret)"; return 1;;
        esac
        if [[ -z $CONTROL_TOKEN ]]; then
          SKIP_REASON="Gateway отвергает токены auth-service, контрольный токен недоступен"; return 1
        fi
        if (( ! NOTE_CONTROL_SHOWN )); then
          NOTE_CONTROL_SHOWN=1
          info "Дальше вместо токена auth-service используется КОНТРОЛЬНЫЙ токен (подписан секретом gateway) — так проверяется остальная бизнес-логика"
        fi
        AUTH_VAL=$CONTROL_TOKEN
      else
        AUTH_VAL=$tok
      fi;;
    @control)
      if [[ -z $CONTROL_TOKEN ]]; then SKIP_REASON="контрольный токен недоступен (нет jwt.secret из Config Server или openssl)"; return 1; fi
      AUTH_VAL=$CONTROL_TOKEN;;
    *) AUTH_VAL=$a;;
  esac
  return 0
}

# ───────────────────────── Карта путей → сервис / контроллер ─────────────────
route_info() {
  local p=${1%%\?*}
  SVC=""; ROUTE=""; CTRL=""; FEIGN=""
  case $p in
    /api/auth/*)       SVC=auth-service; ROUTE=auth-api; CTRL='auth-service/…/controller/AuthController.java  (@RequestMapping("/auth"))';;
    /api/admin*)       SVC=auth-service; ROUTE=admin-api; CTRL='auth-service/…/controller/AdminController.java  (@RequestMapping("/admin"), @PreAuthorize ADMIN)';;
    /api/users*)       SVC=auth-service; ROUTE=users-api; CTRL='auth-service/…/controller/UserController.java  (@RequestMapping("/users"))';;
    /api/products*)    SVC=product-service; ROUTE=product-api; CTRL='product-service/…/controller/ProductController.java  (класс ЗАКОММЕНТИРОВАН)';;
    /api/categories*)  SVC=product-service; ROUTE=product-api; CTRL='product-service/…/controller/CategoryController.java  ("/categories")';;
    /api/components*)  SVC=product-service; ROUTE=product-api; CTRL='product-service/…/controller/ComponentController.java  ("/components")';;
    /api/builds*)      SVC=product-service; ROUTE=product-api; CTRL='product-service/…/controller/BuildController.java  ("/builds")';;
    /api/compatibility/check*) SVC=compatibility-service; ROUTE=compatibility-api
        CTRL='compatibility-service/…/controller/CompatibilityCheckController.java  ("/check")'
        FEIGN='compatibility-service/…/client/ProductClient.java → GET product-service /components/{id}';;
    /api/compatibility/rules*) SVC=compatibility-service; ROUTE=compatibility-api; CTRL='compatibility-service/…/controller/CompatibilityRuleController.java  ("/rules")';;
    /api/compatibility/logs*)  SVC=compatibility-service; ROUTE=compatibility-api; CTRL='compatibility-service/…/controller/LogController.java  ("/logs")';;
    /api/compatibility*)       SVC=compatibility-service; ROUTE=compatibility-api; CTRL='compatibility-service/…/controller/*';;
    /api/order-items*) SVC=order-service; ROUTE="(нет маршрута)"; CTRL='order-service/…/controller/OrderItemController.java  ("/order-items")';;
    /api/status*)      SVC=order-service; ROUTE="(нет маршрута)"; CTRL='order-service/…/controller/OrderStatusController.java  ("/status")';;
    /api/orders*)      SVC=order-service; ROUTE=order-api; CTRL='order-service/…/controller/OrderController.java  ("/orders")'
        FEIGN='order-service/…/client/ProductClient.java → product-service /components/{id} и PATCH /components/{id}/decrease-stock';;
    /v3/api-docs/auth)          SVC=auth-service; ROUTE=auth-swagger;;
    /v3/api-docs/product)       SVC=product-service; ROUTE=product-swagger;;
    /v3/api-docs/compatibility) SVC=compatibility-service; ROUTE=compatibility-swagger;;
    /v3/api-docs/order)         SVC=order-service; ROUTE=order-swagger;;
  esac
}

# ───────────────────────── Диагностика по коду ответа ────────────────────────
diagnose() {
  local code=$HTTP_CODE
  route_info "$HTTP_PATH"
  case $code in
    000) hint "Нет HTTP-ответа от $HTTP_URL${CURL_ERR:+ (curl: $CURL_ERR)}"
         hint "Сервис не запущен / не тот порт / ещё стартует. Порядок старта: eureka-server → config-server → auth, product, order, compatibility → api-gateway.";;
    400|415|422) diag_400;;
    401) diag_401;;
    403) diag_403;;
    404) diag_404;;
    405) hint "Метод $HTTP_METHOD не поддерживается обработчиком ${SVC:-сервиса}."
         [[ -n $CTRL ]] && ref "$CTRL";;
    500) diag_500;;
    502|503|504) diag_gw5xx;;
  esac
}

diag_400() {
  hint "Сервис отклонил запрос ($HTTP_CODE): $(snippet)"
  case ${HTTP_PATH%%\?*} in
    /api/auth/register) hint "Ждёт RegisterRequest {username(3..50), email(@Email), password(≥6)} — auth-service/…/dto/RegisterRequest.java";;
    /api/auth/login)    hint "Ждёт LoginRequest {username, password} — auth-service/…/dto/LoginRequest.java";;
    /api/builds*)       hint "Ждёт BuildRequest {userId, name, items:[{componentId, quantity}]} — product-service/…/dto/BuildRequest.java, BuildItemRequest.java";;
    /api/components*)   hint "Ждёт сущность Component {name(@NotBlank), price(≥0), stockQuantity(≥0), category:{id}} — product-service/…/model/Component.java";;
    /api/orders*)       hint "Ждёт OrderRequest {userId, items:[{componentId, quantity}]} — order-service/…/dto/OrderRequest.java, OrderItemRequest.java";;
    /api/compatibility/check*) hint "Нужны query-параметры sourceId, targetId, fieldToMatch (socket|ramType|formFactor).";;
  esac
}

diag_401() {
  local err; err=$(jqb '.error // empty')
  case $err in
    "Missing or invalid Authorization header")
      hint "JwtAuthFilter (gateway) не нашёл заголовок «Authorization: Bearer <token>» — либо токен не передан/пуст, либо путь ${HTTP_PATH%%\?*} не входит в PUBLIC_PATHS."
      ref "api-gateway/…/filter/JwtAuthFilter.java → PUBLIC_PATHS, filter(): authHeader.startsWith(\"Bearer \")";;
    "Invalid or expired JWT token")
      hint "JwtAuthFilter (gateway) не смог проверить токен: Jwts.parser().verifyWith(getSigningKey()).parseSignedClaims(token) бросил исключение."
      hint "Причины: (1) jwt.secret gateway ≠ jwt.secret auth-service, (2) токен просрочен (jwt.expiration), (3) токен повреждён."
      hint "Точный тип исключения (SignatureException / ExpiredJwtException) виден в логе api-gateway (e.printStackTrace())."
      if (( JWT_MISMATCH )); then hint "‼ Выше уже установлено: jwt.secret в Config Server РАЗНЫЙ у api-gateway и auth-service."
      else hint "Сравните строки «JWT SECRET FINGERPRINT» в логах api-gateway и auth-service."; fi
      ref "config-server/…/config/api-gateway.yaml → jwt.secret   vs   config/auth-service.yaml → jwt.secret";;
    *)
      hint "401 пришёл не от JwtAuthFilter gateway (тело: $(snippet))."
      hint "Смотрите SecurityConfig / security/JwtAuthFilter в ${SVC:-auth-service}.";;
  esac
}

diag_403() {
  case ${HTTP_PATH%%\?*} in
    /api/admin*)
      hint "AdminController защищён @PreAuthorize(\"hasRole('ADMIN')\"): у токена нет ROLE_ADMIN (claim roles)."
      ref "auth-service/…/controller/AdminController.java; DataInitializer (admin создаётся с ROLE_ADMIN); JwtService.generateToken (claim \"roles\")";;
    *)
      hint "403 исходит от Spring Security в ${SVC:-сервисе}: токен прошёл gateway, но запрос не аутентифицирован (нет Authentication → Http403ForbiddenEntryPoint)."
      hint "Проверьте: CustomUserDetailsService.loadUserByUsername(sub), JwtService.isTokenValid(), jwt.secret в auth-service.yaml, SecurityConfig.requestMatchers(...).permitAll()."
      ref "auth-service/…/security/JwtAuthFilter.java, SecurityConfig.java";;
  esac
}

diag_404() {
  local p=${HTTP_PATH%%\?*} bpath gwmark
  bpath=$(jqb '.path // empty'); gwmark=$(jqb '.requestId // empty')
  if (( DIRECT )); then
    hint "${SVC:-Сервис} (напрямую) вернул 404: нет обработчика для «$p» → проверьте @RequestMapping/@…Mapping."
    [[ -n $CTRL ]] && ref "$CTRL"
    return
  fi
  if [[ -n $gwmark || ( -n $bpath && $bpath == "$p" ) ]]; then
    hint "404 сформировал сам Gateway: ни один маршрут (predicate Path=…) не подошёл к «$p»."
    if [[ $ROUTE == "(нет маршрута)" ]]; then
      hint "Контроллер существует в $SVC, но в api-gateway.yaml для него нет маршрута. Добавьте в route order-api:"
      hint "    predicates: - Path=/api/orders/**,/api/order-items/**,/api/status/**   (StripPrefix=1 оставит /order-items/…, /status/…)"
      root_cause order-routes "Gateway не маршрутизирует /api/order-items/** и /api/status/** → OrderItemController и OrderStatusController недоступны снаружи (api-gateway.yaml, route order-api)."
    else
      hint "Добавьте/поправьте маршрут в spring.cloud.gateway.routes (uri: lb://${SVC:-<service>}, Path=$(printf '%s' "$p" | cut -d/ -f1-3)/**, StripPrefix=1)."
    fi
    ref "config-server/src/main/resources/config/api-gateway.yaml  (после правки перезапустите gateway; актуальные маршруты: $GATEWAY_URL/actuator/gateway/routes)"
  elif [[ -n $bpath ]]; then
    hint "Маршрут ${ROUTE:-?} сработал, но ${SVC:-сервис} ответил 404: путь после StripPrefix = «$bpath»."
    case $p in
      /api/compatibility*)
        hint "api-gateway.yaml: route compatibility-api: Path=/api/compatibility/** + StripPrefix=1 → сервис получает /compatibility/…, а контроллеры слушают /check, /rules, /logs (без /compatibility)."
        hint "Исправление: StripPrefix=2  (или добавить @RequestMapping(\"/compatibility/...\") в контроллеры)."
        root_cause compat-strip "api-gateway.yaml, route compatibility-api: StripPrefix=1 даёт /compatibility/check, а в compatibility-service контроллеры на /check, /rules, /logs → ВСЕ /api/compatibility/** = 404. Нужен StripPrefix=2.";;
      /api/products*)
        hint "ProductController в product-service полностью закомментирован → пути /products нет. А frontend-service (CatalogController) вызывает GET /api/products."
        hint "Исправление: раскомментировать/реализовать ProductController, либо перевести frontend на /api/components."
        root_cause products-missing "GET /api/products → 404: ProductController (product-service) закомментирован, а frontend-service CatalogController ходит именно на /api/products.";;
      /v3/api-docs/auth)
        hint "route auth-swagger делает SetPath=/auth/v3/api-docs, но у auth-service springdoc отдаёт /v3/api-docs (контекст-пути /auth нет)."
        hint "Исправление: filters: - SetPath=/v3/api-docs"
        root_cause auth-swagger "api-gateway.yaml, route auth-swagger: SetPath=/auth/v3/api-docs → 404; у auth-service документация на /v3/api-docs.";;
      *)
        hint "В ${SVC:-сервисе} нет маппинга для «$bpath»: сверьте @RequestMapping и StripPrefix маршрута $ROUTE."
        [[ -n $CTRL ]] && ref "$CTRL";;
    esac
  else
    hint "404 без поля path: скорее всего бизнес-404 («$(jqb '.error // empty')»), т.е. маршрут верный, но сущность не найдена."
    [[ -n $CTRL ]] && ref "$CTRL"
  fi
}

diag_500() {
  local msg; msg=$(jqb '.message // .error // empty')
  hint "500 = необработанное исключение в ${SVC:-сервисе}${msg:+ (ответ: $msg)}."
  [[ -n $CTRL ]] && ref "$CTRL"
  [[ -n $FEIGN ]] && hint "Возможный источник — Feign-вызов: $FEIGN"
  case ${HTTP_PATH%%\?*} in
    /api/auth/*)
      hint "AuthService бросает голый RuntimeException (\"Username already exists\", \"ROLE_USER not found\", …), а @ControllerAdvice в auth-service нет → клиент видит 500.";;
    /api/orders*)
      hint "OrderController.createOrder: items==null → NPE; ProductClientFallback.getComponentById бросает RuntimeException, если product-service недоступен или вернул 404.";;
    /api/compatibility/check*)
      hint "CompatibilityCheckController: Feign ProductClient вернул null/DTO без нужных полей (ComponentDto).";;
  esac
  hint "Стектрейс — в логе ${SVC:-сервиса} (spring.jpa.show-sql=true покажет и SQL)."
}

diag_gw5xx() {
  local err; err=$(jqb '.error // empty')
  if [[ $err == *"временно недоступен"* ]]; then
    hint "Сработал FallbackController gateway (CircuitBreaker маршрута ${ROUTE:-?}): ${SVC:-сервис} недоступен или breaker открыт (failureRate ≥ 50%, waitDurationInOpenState=10s)."
    hint "Проверьте: ${SVC:-сервис} зарегистрирован в Eureka (UP)? В логе gateway: «Unable to find instance for ${SVC:-…}» / таймаут?"
    ref "api-gateway.yaml: route ${ROUTE:-?} → uri: lb://${SVC:-…}; resilience4j.circuitbreaker.instances.*"
  else
    hint "Ответ $HTTP_CODE: $(snippet)"
  fi
}

# ─────────────────────────────── Проверки ────────────────────────────────────
pretty() { local s=${1//4\[0-9\]\[0-9\]/4xx}; s=${s//5\[0-9\]\[0-9\]/5xx}; printf '%s' "$s"; }
# req "Имя" МЕТОД ПУТЬ 'regex-кодов' [ТЕЛО] [ТОКЕН]  → FAIL + диагностика при несовпадении
req() {
  local name=$1 method=$2 path=$3 want=$4 data=${5-} auth=${6-}
  if ! prepare_auth "$auth" "$path"; then HTTP_CODE=000; HTTP_BODY=""; skip "$name" "$SKIP_REASON"; return 2; fi
  http "$method" "$path" "$data" "$AUTH_VAL"
  if [[ $HTTP_CODE =~ ^($want)$ ]]; then
    pass "$name" "[$method ${path%%\?*} → $HTTP_CODE, ${HTTP_MS}ms]"
    (( VERBOSE )) && printf '      %s%s%s\n' "$D" "$(snippet)" "$N"
    return 0
  fi
  fail "$name" "$method $path → HTTP $HTTP_CODE (ожидали $(pretty "$want"))"
  diagnose
  return 1
}

# req_soft … 'OK-коды' 'WARN-коды' [ТЕЛО] [ТОКЕН] ["подсказка при WARN"] ["подсказка при FAIL"]
req_soft() {
  local name=$1 method=$2 path=$3 ok=$4 wn=$5 data=${6-} auth=${7-} wmsg=${8-} fmsg=${9-}
  if ! prepare_auth "$auth" "$path"; then HTTP_CODE=000; HTTP_BODY=""; skip "$name" "$SKIP_REASON"; return 2; fi
  http "$method" "$path" "$data" "$AUTH_VAL"
  if [[ $HTTP_CODE =~ ^($ok)$ ]]; then
    pass "$name" "[$method ${path%%\?*} → $HTTP_CODE, ${HTTP_MS}ms]"; return 0
  fi
  if [[ -n $wn && $HTTP_CODE =~ ^($wn)$ ]]; then
    warn "$name" "$method $path → HTTP $HTTP_CODE (корректно: $(pretty "$ok"))"
    [[ -n $wmsg ]] && hint "$wmsg"
    return 1
  fi
  fail "$name" "$method $path → HTTP $HTTP_CODE (ожидали $(pretty "$ok"))"
  diagnose
  [[ -n $fmsg ]] && hint "$fmsg"
  return 1
}

_check() { # режим(f|w) имя json jq-условие [подсказка]
  local mode=$1 name=$2 json=$3 filter=$4 msg=${5-}
  if printf '%s' "$json" | jq -e "$filter" >/dev/null 2>&1; then pass "$name"; return 0; fi
  if [[ $mode == w ]]; then warn "$name" "jq-условие не выполнено: $filter"
  else fail "$name" "jq-условие не выполнено: $filter"; fi
  hint "Фактический ответ: $(printf '%s' "$json" | tr '\n\r' '  ' | cut -c1-300)"
  [[ -n $msg ]] && hint "$msg"
  return 1
}
check()  { _check f "$1" "$HTTP_BODY" "$2" "${3-}"; }   # по телу последнего ответа
checkw() { _check w "$1" "$HTTP_BODY" "$2" "${3-}"; }
checkj() { _check f "$1" "$2" "$3" "${4-}"; }          # по произвольному JSON

# ─────────────────────────────── Утилиты JWT ─────────────────────────────────
b64url()  { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
jwt_payload() {
  local s=${1#*.}; s=${s%%.*}; s=${s//-/+}; s=${s//_//}
  case $(( ${#s} % 4 )) in 2) s+='==';; 3) s+='=';; esac
  printf '%s' "$s" | base64 -d 2>/dev/null
}
make_jwt() { # секрет payload-json [alg]
  local secret=$1 payload=$2 alg=${3:-HS256} h p s
  h=$(printf '{"alg":"%s","typ":"JWT"}' "$alg" | b64url)
  p=$(printf '%s' "$payload" | b64url)
  if [[ $alg == none ]]; then printf '%s.%s.' "$h" "$p"; return; fi
  s=$(printf '%s' "$h.$p" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)
  printf '%s.%s.%s' "$h" "$p" "$s"
}
sha256_of() { printf '%s' "$1" | { sha256sum 2>/dev/null || shasum -a 256 2>/dev/null || openssl dgst -sha256 -r; } | awk '{print $1}'; }
cfg_secret() { # app → jwt.secret из Config Server
  curl -sS -m "$TIMEOUT" -H 'Accept: application/json' "$CONFIG_URL/$1/default" 2>/dev/null \
    | jq -r '[.propertySources[]?.source["jwt.secret"]? | select(. != null)] | first // empty' 2>/dev/null | tr -d '\r'
}

# ─────────────────────────────── Очистка ─────────────────────────────────────
add_cleanup() { CLEANUP+=("$1:$2"); }    # вид(user|admin):путь, выполняется в обратном порядке
cleanup_all() {
  (( CLEANED )) && return 0
  CLEANED=1
  (( ${#CLEANUP[@]} )) || return 0
  local i entry kind path tok ok=0 bad=0 old_timeout=$TIMEOUT
  TIMEOUT=5
  for (( i=${#CLEANUP[@]}-1; i>=0; i-- )); do
    entry=${CLEANUP[$i]}; kind=${entry%%:*}; path=${entry#*:}
    if [[ $kind == admin ]]; then tok=$ADMIN_TOKEN; else tok=$USER_TOKEN; fi
    if (( JWT_BLOCKED && ! DIRECT )) || [[ -z $tok ]]; then tok=$CONTROL_TOKEN; fi
    http DELETE "$path" "" "$tok"
    if [[ $HTTP_CODE =~ ^(200|204|404)$ ]]; then ok=$((ok+1)); else bad=$((bad+1)); fi
  done
  TIMEOUT=$old_timeout
  printf '\n%s🧹 Очистка тестовых данных: удалено %d, не удалось %d%s\n' "$D" "$ok" "$bad" "$N"
}

# ═════════════════════════════ 1. ИНФРАСТРУКТУРА ═════════════════════════════
health_check() { # имя базовый-URL
  local name=$1 base=$2 bad
  http GET "$base/actuator/health"
  if [[ $HTTP_CODE == 000 ]]; then
    fail "$name: GET /actuator/health" "нет ответа от $base"
    hint "Сервис не запущен или порт неверный${CURL_ERR:+ (curl: $CURL_ERR)}."
    return 1
  fi
  if [[ $HTTP_CODE == 200 ]] && jqt '.status=="UP"'; then
    pass "$name: status=UP" "[${HTTP_MS}ms]"; return 0
  fi
  fail "$name: GET /actuator/health" "HTTP $HTTP_CODE, status=$(jqb '.status // "?"')"
  bad=$(jqb '[.components // {} | to_entries[] | select(.value.status != "UP") | .key] | join(", ")')
  [[ -n $bad ]] && hint "Компоненты не UP: $bad"
  case $bad in *db*)        hint "БД недоступна: docker compose up -d; в init-db.sql должны создаваться auth_db, product_db, order_db, compatibility_db.";; esac
  case $bad in *discovery*) hint "Нет связи с Eureka ($EUREKA_URL).";; esac
  case $bad in *circuit*)   hint "Открыт Circuit Breaker (resilience4j): были сбои вызовов.";; esac
  return 1
}

sec_infra() {
  section "1. Инфраструктура: Config Server, Eureka, сервисы, маршруты Gateway"
  local app svc st id

  health_check "Eureka (8761)" "$EUREKA_URL"
  health_check "Config Server (8888)" "$CONFIG_URL"
  for app in api-gateway auth-service product-service order-service compatibility-service; do
    http GET "$CONFIG_URL/$app/default"
    if [[ $HTTP_CODE == 200 ]] && jqt '(.propertySources|length) > 0'; then
      pass "Config Server отдаёт конфиг «$app»" "[$(jqb '.propertySources|length') источн.]"
    else
      fail "Config Server отдаёт конфиг «$app»" "HTTP $HTTP_CODE, propertySources пуст"
      hint "Файл $app.yaml должен лежать в config-server/src/main/resources/config/ (search-locations: classpath:/config/); пересоберите config-server."
    fi
  done

  health_check "API Gateway (8080)" "$GATEWAY_URL"
  [[ $HTTP_CODE == 000 ]] && GW_DOWN=1
  health_check "Auth-service (8081)" "$AUTH_URL"
  health_check "Product-service (8082)" "$PRODUCT_URL"
  health_check "Order-service (8083)" "$ORDER_URL"
  health_check "Compatibility-service (8084)" "$COMPAT_URL"

  # Регистрация в Eureka
  http GET "$EUREKA_URL/eureka/apps"
  if [[ $HTTP_CODE != 200 ]]; then
    fail "Eureka: реестр приложений" "GET /eureka/apps → HTTP $HTTP_CODE"
    hint "Eureka недоступна: lb://… в маршрутах gateway и Feign-клиенты работать не будут."
  else
    for svc in API-GATEWAY AUTH-SERVICE PRODUCT-SERVICE ORDER-SERVICE COMPATIBILITY-SERVICE; do
      st=$(jqb --arg n "$svc" '[.applications.application] | flatten | map(select(.name==$n)) | first | (.instance // empty) | [.] | flatten | map(.status) | join(",")')
      if [[ $st == UP* ]]; then pass "Eureka: $svc зарегистрирован" "[$st]"
      elif [[ -z $st ]]; then
        fail "Eureka: $svc зарегистрирован" "в реестре нет приложения $svc"
        hint "Сервис не запущен/не дошёл до Eureka. Проверьте eureka.client.service-url.defaultZone и spring.application.name (config-server …/config/$(printf '%s' "$svc" | tr 'A-Z' 'a-z').yaml)."
      else
        fail "Eureka: $svc зарегистрирован" "статус: $st (ожидали UP)"
        hint "Инстанс есть, но не UP — смотрите /actuator/health этого сервиса."
      fi
    done
  fi

  # Маршруты Gateway
  http GET "$GATEWAY_URL/actuator/gateway/routes"
  if [[ $HTTP_CODE == 200 ]] && jqt 'type=="array"'; then
    for id in auth-swagger product-swagger compatibility-swagger order-swagger auth-api admin-api users-api product-api compatibility-api order-api; do
      if [[ -n $(jqb --arg i "$id" '.[] | select(.route_id==$i) | .route_id') ]]; then
        pass "Gateway: маршрут «$id» загружен"
      else
        fail "Gateway: маршрут «$id» загружен" "в /actuator/gateway/routes его нет"
        hint "Нет route id=$id в config-server/…/config/api-gateway.yaml (или gateway стартовал без Config Server: spring.config.import=optional:configserver: молча игнорирует его недоступность)."
      fi
    done
  else
    fail "Gateway: /actuator/gateway/routes" "HTTP $HTTP_CODE"
    hint "Эндпоинт gateway не опубликован: management.endpoints.web.exposure.include должен содержать gateway (api-gateway.yaml)."
  fi
}

# ═════════════════════════════ 2. SWAGGER ════════════════════════════════════
sec_swagger() {
  section "2. Swagger / OpenAPI через Gateway"
  local svc exp t
  HTTP_ACCEPT='text/html' req_soft "Swagger UI: GET /swagger-ui.html" GET /swagger-ui.html '200|301|302|303|307|308' '' "" "" "" \
    "Gateway: springdoc-openapi-starter-webflux-ui; /swagger-ui.html и /webjars/** должны быть в PUBLIC_PATHS (JwtAuthFilter)."
  if req "Swagger UI: swagger-config" GET /v3/api-docs/swagger-config 200; then
    checkw "swagger-config содержит 4 сервиса (springdoc.swagger-ui.urls)" '(.urls // []) | length >= 4' \
      "springdoc.swagger-ui.urls в api-gateway.yaml не подхватился."
  fi
  for svc in auth product compatibility order; do
    if req "Swagger: GET /v3/api-docs/$svc" GET "/v3/api-docs/$svc" 200; then
      check "  ↳ /v3/api-docs/$svc — валидный OpenAPI (openapi + paths)" '(.openapi != null) and ((.paths|length) > 0)' \
        "Сервис отдал не OpenAPI-JSON — проверьте springdoc в $svc-service и SetPath маршрута $svc-swagger."
      case $svc in auth) exp=Auth;; product) exp=Product;; compatibility) exp=Compatibility;; order) exp=Order;; esac
      t=$(jqb '.info.title // empty')
      if [[ $t != *"$exp"* ]]; then
        warn "  ↳ $svc: info.title=«$t» не соответствует сервису" "ожидалось «$exp …»"
        hint "OpenApiConfig.java в $svc-service скопирован из compatibility-service (title/description). Поправьте."
      fi
    fi
  done
}

# ═════════════════════════════ 3. AUTH / JWT ═════════════════════════════════
sec_auth() {
  section "3. Auth-service: регистрация, логин, JWT"
  local body fp_g fp_a i diff_at pl now

  # 3.0 jwt.secret из Config Server
  GW_SECRET=$(cfg_secret api-gateway); AU_SECRET=$(cfg_secret auth-service)
  if [[ -z $GW_SECRET || -z $AU_SECRET ]]; then
    warn "jwt.secret api-gateway vs auth-service" "не удалось прочитать из Config Server ($CONFIG_URL)"
    hint "Сравните строки «JWT SECRET FINGERPRINT» в логах api-gateway и auth-service вручную."
  elif [[ $GW_SECRET == "$AU_SECRET" ]]; then
    pass "jwt.secret одинаков у api-gateway и auth-service" "[sha256 $(sha256_of "$GW_SECRET" | cut -c1-12)…]"
  else
    JWT_MISMATCH=1
    fp_g=$(sha256_of "$GW_SECRET"); fp_a=$(sha256_of "$AU_SECRET")
    diff_at=0
    for (( i=0; i<${#GW_SECRET} && i<${#AU_SECRET}; i++ )); do
      [[ ${GW_SECRET:$i:1} != "${AU_SECRET:$i:1}" ]] && { diff_at=$((i+1)); break; }
    done
    (( diff_at == 0 )) && diff_at=$(( (${#GW_SECRET} < ${#AU_SECRET} ? ${#GW_SECRET} : ${#AU_SECRET}) + 1 ))
    fail "jwt.secret РАЗЛИЧАЕТСЯ у api-gateway и auth-service" "gateway: ${#GW_SECRET} симв., sha256 ${fp_g:0:12}… │ auth: ${#AU_SECRET} симв., sha256 ${fp_a:0:12}…"
    hint "Первое расхождение — позиция $diff_at. Auth подписывает токены одним ключом, gateway проверяет другим → ВСЕ запросы с токеном получают 401."
    hint "Исправление: держать секрет в одном месте — config-server/…/config/application.yaml (общий для всех приложений) либо env JWT_SECRET."
    ref "config-server/…/config/api-gateway.yaml → jwt.secret   и   …/auth-service.yaml → jwt.secret"
    root_cause jwt-secret "jwt.secret в api-gateway.yaml и auth-service.yaml НЕ совпадает (длина ${#GW_SECRET} vs ${#AU_SECRET}, расхождение с позиции $diff_at) → gateway отклоняет каждый токен, выданный auth-service (401 «Invalid or expired JWT token»)."
  fi

  # Контрольный токен: подписан секретом gateway
  if [[ -n $GW_SECRET ]] && (( HAVE_OPENSSL )); then
    now=$(date +%s)
    CONTROL_TOKEN=$(make_jwt "$GW_SECRET" "{\"sub\":\"e2e-control\",\"userId\":0,\"roles\":[\"ROLE_USER\"],\"iat\":$now,\"exp\":$((now+3600))}")
  fi

  # 3.1 Регистрация
  body=$(jq -nc --arg u "$USER_NAME" --arg e "$USER_EMAIL" --arg p "$USER_PASS" '{username:$u,email:$e,password:$p}')
  if req "POST /api/auth/register — новый пользователь" POST /api/auth/register '200|201' "$body"; then
    USER_TOKEN=$(jqb '.token // empty')
    check "  ↳ AuthResponse: token, username, roles ∋ ROLE_USER" \
      "(.token|type==\"string\" and length>20) and .username==\"$USER_NAME\" and ((.roles // [])|index(\"ROLE_USER\")) != null" \
      "AuthResponse(token, username, roles) — auth-service/…/dto/AuthResponse.java, AuthService.register()"
  fi
  req "POST /api/auth/register — невалидные данные → 400" POST /api/auth/register 400 \
      '{"username":"ab","email":"not-an-email","password":"1"}' || true
  req_soft "POST /api/auth/register — повторная регистрация → 4xx" POST /api/auth/register '400|409|422' '500' "$body" "" \
      "AuthService.register() кидает RuntimeException(\"Username already exists\"), а @ControllerAdvice в auth-service нет → 500 вместо 409 (auth-service/…/service/AuthService.java)." \
      "Повторная регистрация того же логина ПРОШЛА — нет проверки уникальности." || true

  # 3.2 Логин
  body=$(jq -nc --arg u "$USER_NAME" --arg p "$USER_PASS" '{username:$u,password:$p}')
  if req "POST /api/auth/login — пользователь" POST /api/auth/login 200 "$body"; then
    USER_TOKEN=$(jqb '.token // empty')
    check "  ↳ в ответе есть token" '.token|type=="string" and length>20'
  fi
  body=$(jq -nc --arg u "$ADMIN_USER" --arg p "$ADMIN_PASS" '{username:$u,password:$p}')
  if req "POST /api/auth/login — админ ($ADMIN_USER)" POST /api/auth/login 200 "$body"; then
    ADMIN_TOKEN=$(jqb '.token // empty')
    check "  ↳ у админа roles ∋ ROLE_ADMIN" '(.roles // [])|index("ROLE_ADMIN") != null' \
      "auth-service/…/config/DataInitializer создаёт admin/admin123 только при пустой таблице roles."
  else
    hint "Админ создаётся DataInitializer (admin / admin123) только если таблица roles пуста — если БД уже была заполнена, задайте ADMIN_USER/ADMIN_PASS."
  fi
  body=$(jq -nc --arg u "$USER_NAME" '{username:$u,password:"wrong-password"}')
  req "POST /api/auth/login — неверный пароль → 4xx" POST /api/auth/login '400|401|403' "$body" || true

  # 3.3 Содержимое JWT
  if [[ -n $USER_TOKEN ]]; then
    pl=$(jwt_payload "$USER_TOKEN")
    USER_ID=$(printf '%s' "$pl" | jq -r '.userId // empty' 2>/dev/null | tr -d '\r')
    checkj "JWT: claims sub, userId, roles, exp" "$pl" \
      ".sub==\"$USER_NAME\" and (.userId|type==\"number\") and ((.roles|type)==\"array\") and (.exp > $(date +%s))" \
      "Gateway читает claims userId/roles (JwtAuthFilter), создаёт их auth-service/…/security/JwtService.generateToken()."
  fi

  # 3.4 Принимает ли Gateway токен auth-service
  if (( DIRECT )); then
    info "Режим --direct: проверка «Gateway принимает токен» пропущена."
  elif [[ -z $USER_TOKEN ]]; then
    skip "Gateway принимает токен auth-service" "нет токена"
  elif ! req "Gateway принимает токен, выданный auth-service" GET /api/categories 200 "" @user; then
    if [[ $HTTP_CODE == 401 && $(jqb '.error // empty') == *Invalid* ]]; then JWT_BLOCKED=1; fi
  fi

  # 3.5 /users/me
  if req "GET /api/users/me — профиль по токену" GET /api/users/me 200 "" @user; then
    check "  ↳ username == $USER_NAME" ".username==\"$USER_NAME\" and (.roles|index(\"ROLE_USER\")) != null" \
      "UserController.getCurrentUser: SecurityContextHolder → findByUsername"
    [[ -z $USER_ID ]] && USER_ID=$(jqb '.id // empty')
  fi
  [[ -n $USER_ID ]] && add_cleanup admin "/api/admin/users/$USER_ID"   # только id НАШЕГО тестового пользователя
  UID_N=${USER_ID:-0}                                                  # для тел запросов
}

# ═════════════════════════════ 4. БЕЗОПАСНОСТЬ ═══════════════════════════════
sec_security() {
  section "4. Безопасность: 401 без токена, подделки, RBAC"
  local ep now expd forged none_tok rnd

  if (( DIRECT )); then skip "Проверки безопасности Gateway" "режим --direct"; return; fi

  for ep in /api/categories /api/components /api/builds /api/orders /api/compatibility/rules /api/users/me /api/admin/users; do
    if ! req "401 без токена: GET $ep" GET "$ep" 401; then
      [[ $HTTP_CODE =~ ^2 ]] && hint "Эндпоинт открыт без токена! Проверьте PUBLIC_PATHS в JwtAuthFilter (gateway) — слишком широкий шаблон?"
    fi
  done
  req "401: мусорный Bearer-токен" GET /api/categories 401 "" "garbage.token.value" || true
  req "401: схема Basic вместо Bearer" GET /api/categories 401 "" "RAW:Basic dXNlcjpwYXNz" || true

  if (( HAVE_OPENSSL )); then
    now=$(date +%s)
    rnd=$(printf '%s' "$E2E_ID-wrong-secret-0123456789abcdef0123456789" | cut -c1-44)
    forged=$(make_jwt "$rnd" "{\"sub\":\"admin\",\"userId\":1,\"roles\":[\"ROLE_ADMIN\"],\"iat\":$now,\"exp\":$((now+3600))}")
    req "401: токен admin, подписанный ЧУЖИМ секретом" GET /api/admin/users 401 "" "$forged" || \
      { [[ $HTTP_CODE =~ ^2 ]] && hint "Gateway принял подделку! Проверьте, что JwtAuthFilter использует verifyWith(...) (а не parseUnsecuredClaims)."; }
    none_tok=$(make_jwt "" "{\"sub\":\"admin\",\"userId\":1,\"roles\":[\"ROLE_ADMIN\"],\"exp\":$((now+3600))}" none)
    req "401: токен с alg=none" GET /api/admin/users 401 "" "$none_tok" || true
    if [[ -n $GW_SECRET ]]; then
      expd=$(make_jwt "$GW_SECRET" "{\"sub\":\"e2e\",\"userId\":0,\"roles\":[\"ROLE_USER\"],\"iat\":$((now-7200)),\"exp\":$((now-3600))}")
      req "401: просроченный токен (подпись верная)" GET /api/categories 401 "" "$expd" || true
      if req "Контроль: токен, подписанный секретом gateway, проходит JwtAuthFilter" GET /api/categories 200 "" @control; then
        if (( JWT_BLOCKED )); then
          hint "Токен gateway принят, а токен auth-service — нет ⇒ gateway исправен; проблема ТОЛЬКО в том, что auth-service подписывает другим jwt.secret."
          root_cause jwt-secret "Контрольный токен (секрет gateway) проходит, токен auth-service — нет ⇒ несовпадение jwt.secret между api-gateway и auth-service."
        fi
      fi
    fi
  else
    skip "Проверки подделанных токенов" "нет openssl"
  fi

  # RBAC
  req "RBAC: USER → /api/admin/users = 403" GET /api/admin/users 400 "" @user || \
    { [[ $HTTP_CODE == 200 ]] && hint "Обычный пользователь прочитал список всех пользователей: @PreAuthorize не сработал (нужен @EnableMethodSecurity — он есть) или у токена роль ADMIN."; }
  if req "RBAC: ADMIN → /api/admin/users = 200" GET /api/admin/users 200 "" @admin; then
    check "  ↳ в списке есть пользователь «$ADMIN_USER»" "map(.username) | index(\"$ADMIN_USER\") != null"
  fi

  # Обход Gateway
  local pair name url
  for pair in "product-service|$PRODUCT_URL/components" "order-service|$ORDER_URL/orders" "compatibility-service|$COMPAT_URL/rules"; do
    name=${pair%%|*}; url=${pair#*|}
    http GET "$url"
    if [[ $HTTP_CODE == 000 ]]; then skip "Прямой доступ к $name без токена" "сервис недоступен"
    elif [[ $HTTP_CODE =~ ^2 ]]; then
      warn "Прямой доступ к $name без токена = $HTTP_CODE" "JWT проверяется только на Gateway"
      hint "У $name нет Spring Security: любой, кто знает порт, обходит авторизацию. Закройте порты (docker network/firewall) или проверяйте подпись/X-User-* в сервисе."
    else pass "Прямой доступ к $name без токена закрыт" "[$HTTP_CODE]"; fi
  done
}

# ═════════════════════════════ 5. PRODUCT ════════════════════════════════════
mk_component() { # имя цена остаток categoryId [socket] [ramType] [formFactor] → MK_ID
  local b
  MK_ID=""
  if [[ -z ${4-} ]]; then skip "Создать компонент «$1»" "неизвестна категория"; return 1; fi
  b=$(jq -nc --arg n "$1" --argjson p "$2" --argjson s "$3" --argjson c "$4" \
        --arg so "${5-}" --arg rt "${6-}" --arg ff "${7-}" \
        '{name:$n,manufacturer:"E2E",price:$p,stockQuantity:$s,category:{id:$c}}
         + (if $so!="" then {socket:$so} else {} end)
         + (if $rt!="" then {ramType:$rt} else {} end)
         + (if $ff!="" then {formFactor:$ff} else {} end)')
  if req "POST /api/components — создать «$1»" POST /api/components 201 "$b" @user; then
    MK_ID=$(jqb '.id // empty')
    [[ -n $MK_ID ]] && add_cleanup user "/api/components/$MK_ID"
    check "  ↳ в ответе id, stockQuantity=$3, category.id=$4" ".id != null and .stockQuantity==$3 and .category.id==$4" \
      "ComponentController.create: category подтягивается по category.id"
    return 0
  fi
  return 1
}

sec_product() {
  section "5. Product-service: категории, компоненты, сборки"
  local body tmp cat_id b_id item_id total

  # Категории
  if req "GET /api/categories" GET /api/categories 200 "" @user; then
    check "8 категорий из DataInitializer (CPU … COOLER)" \
      '(["CPU","GPU","MOTHERBOARD","RAM","PSU","CASE","STORAGE","COOLER"] - map(.name)) | length == 0' \
      "product-service/…/config/DataInitializer создаёт категории только при пустой таблице."
    CAT_CPU=$(jqb '.[]|select(.name=="CPU")|.id'); CAT_MB=$(jqb '.[]|select(.name=="MOTHERBOARD")|.id')
    CAT_RAM=$(jqb '.[]|select(.name=="RAM")|.id'); CAT_CASE=$(jqb '.[]|select(.name=="CASE")|.id')
  fi
  # CRUD категории
  if req "POST /api/categories — создать" POST /api/categories 201 "{\"name\":\"E2E_CAT_$E2E_ID\"}" @user; then
    cat_id=$(jqb '.id // empty')
    [[ -n $cat_id ]] && add_cleanup user "/api/categories/$cat_id"
    req "PUT /api/categories/{id} — переименовать" PUT "/api/categories/$cat_id" 200 "{\"name\":\"E2E_CAT_${E2E_ID}_x\"}" @user && \
      check "  ↳ имя обновилось" ".name==\"E2E_CAT_${E2E_ID}_x\""
    req "DELETE /api/categories/{id}" DELETE "/api/categories/$cat_id" '200|204' "" @user
    req "GET /api/categories/{id} после удаления → 404" GET "/api/categories/$cat_id" 404 "" @user || true
  fi

  # Компоненты
  if req "GET /api/components?size=100 — пагинация" GET "/api/components?size=100" 200 "" @user; then
    check "Page<Component>: content[] и totalElements" '((.content|type)=="array") and ((.totalElements|type)=="number")' \
      "ComponentController.getAll должен возвращать Page<Component>"
    checkw "seed: ≥ 6 компонентов из DataInitializer" '.totalElements >= 6' \
      "product-service/…/config/DataInitializer создаёт 6 компонентов только если таблица components пуста."
  fi
  if req "GET /api/components?search=Ryzen — поиск" GET "/api/components?search=Ryzen" 200 "" @user; then
    checkw "поиск находит «Ryzen 5 7600X» (seed)" '[.content[]|select(.name|test("Ryzen";"i"))]|length >= 1'
  fi
  req "GET /api/components/999999999 → 404" GET /api/components/999999999 404 "" @user || true
  req "POST /api/components с price=-5 → 400 (@Min)" POST /api/components 400 \
      '{"name":"bad","price":-5,"stockQuantity":1}' @user || true

  # PUT / PATCH decrease-stock / DELETE — на временном компоненте
  if mk_component "E2E TMP $E2E_ID" 100.5 10 "$CAT_CPU"; then
    tmp=$MK_ID
    body=$(jq -nc --argjson c "$CAT_CPU" --arg n "E2E TMP $E2E_ID" '{name:$n,price:111.11,stockQuantity:12,category:{id:$c}}')
    req "PUT /api/components/{id} — обновить цену и остаток" PUT "/api/components/$tmp" 200 "$body" @user && \
      check "  ↳ price=111.11, stockQuantity=12" '.price==111.11 and .stockQuantity==12'
    req "PATCH /components/{id}/decrease-stock?quantity=2 (эндпоинт для Feign order-service)" \
        PATCH "/api/components/$tmp/decrease-stock?quantity=2" 200 "" @user && \
      check "  ↳ остаток 12 → 10" '.stockQuantity==10'
    req "PATCH decrease-stock больше остатка → 400" PATCH "/api/components/$tmp/decrease-stock?quantity=999" 400 "" @user || true
    req "DELETE /api/components/{id}" DELETE "/api/components/$tmp" '200|204' "" @user
    req "GET удалённого компонента → 404" GET "/api/components/$tmp" 404 "" @user || true
  fi

  # Компоненты для сборок / совместимости (данные под контролем теста)
  mk_component "E2E CPU $E2E_ID"  1500.5 10 "$CAT_CPU"  "E2E_SOCK_A_$E2E_ID" && C_CPU=$MK_ID
  mk_component "E2E RAM $E2E_ID"  400.25 20 "$CAT_RAM"  ""                    "DDR5" && C_RAM=$MK_ID
  mk_component "E2E MB-A $E2E_ID" 3000   5  "$CAT_MB"   "E2E_SOCK_A_$E2E_ID" "DDR5" "ATX"  && C_MB=$MK_ID
  mk_component "E2E MB-B $E2E_ID" 2500   5  "$CAT_MB"   "E2E_SOCK_B_$E2E_ID" "DDR4" "mATX" && C_MB2=$MK_ID
  mk_component "E2E CASE $E2E_ID" 1000   5  "$CAT_CASE" ""                    ""     "ATX"  && C_CASE=$MK_ID

  # /api/products — маршрут есть, а контроллера нет
  req "GET /api/products (его вызывает frontend-service)" GET /api/products 404 "" @user || true

  # Сборки
  if req "GET /api/builds" GET /api/builds 200 "" @user; then
    check "demo-сборка из DataInitializer существует" 'length >= 1' "DataInitializer создаёт «Игровой ПК (demo)» при пустой таблице builds."
  fi
  if [[ -z $C_CPU || -z $C_RAM || -z $C_MB ]]; then
    skip "Сценарий сборки (create/add/remove/delete)" "не созданы тестовые компоненты"
  else
    body=$(jq -nc --argjson u "$UID_N" --arg n "E2E build $E2E_ID" --argjson a "$C_CPU" --argjson b "$C_RAM" \
           '{userId:$u,name:$n,items:[{componentId:$a,quantity:1},{componentId:$b,quantity:2}]}')
    if req "POST /api/builds — создать сборку (CPU×1 + RAM×2)" POST /api/builds '200|201' "$body" @user; then
      b_id=$(jqb '.id // empty')
      [[ -n $b_id ]] && add_cleanup user "/api/builds/$b_id"
      check "  ↳ totalPrice = 1500.5 + 2×400.25 = 2301, items=2, userId=$UID_N" \
        ".totalPrice==2301 and (.items|length)==2 and .userId==$UID_N" \
        "BuildController.create пересчитывает totalPrice = Σ price×quantity (product-service/…/controller/BuildController.java)"
      req "GET /api/builds/{id}" GET "/api/builds/$b_id" 200 "" @user && check "  ↳ items[].component.id присутствуют" "[.items[].component.id] | length == 2"
      req "GET /api/builds?userId=$UID_N" GET "/api/builds?userId=$UID_N" 200 "" @user && \
        check "  ↳ в списке есть наша сборка" "any(.[]; .id==$b_id)"

      body=$(jq -nc --argjson c "$C_MB" '{componentId:$c,quantity:1}')
      req "POST /api/builds/{id}/items — добавить материнскую плату" POST "/api/builds/$b_id/items" 200 "$body" @user && \
        check "  ↳ totalPrice пересчитан: 2301 + 3000 = 5301" '.totalPrice==5301 and (.items|length)==3'
      if req "GET /api/builds/{id} — найти id позиции" GET "/api/builds/$b_id" 200 "" @user; then
        item_id=$(jqb --argjson c "$C_MB" '[.items[]|select(.component.id==$c)][0].id // empty')
        if [[ -n $item_id ]]; then
          req "DELETE /api/builds/{id}/items/{itemId}" DELETE "/api/builds/$b_id/items/$item_id" 200 "" @user && \
            check "  ↳ totalPrice вернулся к 2301" '.totalPrice==2301 and (.items|length)==2'
        else
          fail "Найти itemId добавленной позиции" "в ответе нет items[].id"
        fi
      fi
      req "DELETE /api/builds/{id}" DELETE "/api/builds/$b_id" '200|204' "" @user
      req "GET /api/builds/{id} после удаления → 404" GET "/api/builds/$b_id" 404 "" @user || true
    fi

    body=$(jq -nc --argjson u "$UID_N" '{userId:$u,name:"bad",items:[{componentId:999999999,quantity:1}]}')
    req "POST /api/builds с несуществующим компонентом → 404" POST /api/builds 404 "$body" @user || \
      hint "GlobalExceptionHandler (product-service) мапит RuntimeException с текстом «не найден» → 404, остальные → 400."

    body=$(jq -nc --arg n "E2E spoof $E2E_ID" --argjson a "$C_CPU" '{userId:999999,name:$n,items:[{componentId:$a,quantity:1}]}')
    req_soft "Безопасность: сборка с ЧУЖИМ userId отклоняется" POST /api/builds '401|403|4[0-9][0-9]' '200|201' "$body" @user \
      "BuildController берёт userId из тела запроса, а не из заголовка X-User-Id (его выставляет JwtAuthFilter) → можно создавать/читать сборки от имени другого пользователя." || \
      { [[ $HTTP_CODE =~ ^(200|201)$ ]] && add_cleanup user "/api/builds/$(jqb '.id // empty')"; }
  fi
}

# ═════════════════════════════ 6. COMPATIBILITY ══════════════════════════════
compat_check() { # имя src tgt поле ожидаемое(true|false)
  local q="/api/compatibility/check?sourceId=$2&targetId=$3&fieldToMatch=$4"
  if req "$1" GET "$q" 200 "" @user; then
    if jqt '(.source // "") + (.target // "") | test("недоступен")'; then
      fail "  ↳ сработал Feign-fallback" "source=«$(jqb '.source')», target=«$(jqb '.target')»"
      hint "compatibility-service/…/client/ProductClientFallback вернул заглушку «Компонент временно недоступен» — product-service не отдал компонент."
      hint "Проверьте Feign ProductClient: GET http://product-service/components/{id} (имя в Eureka, circuit breaker «product-service»)."
      return 1
    fi
    check "  ↳ compatible == $5 ($(jqb '.reason // ""'))" ".compatible == $5" \
      "CompatibilityCheckController сравнивает поле «$4» у ComponentDto (id=$2 и id=$3)."
  fi
}

sec_compat() {
  section "6. Compatibility-service (Gateway → Feign → product-service)"
  local logs_before logs_after rid body

  if req "GET /api/compatibility/rules" GET /api/compatibility/rules 200 "" @user; then
    check "3 правила из DataInitializer" 'length >= 3' "compatibility-service/…/config/DataInitializer"
    check "поля CompatibilityRule (sourceCategory, targetCategory, matchingField)" \
      '.[0] | has("sourceCategory") and has("targetCategory") and has("matchingField")'
  fi
  body='{"sourceCategory":"E2E_A","targetCategory":"E2E_B","matchingField":"socket","description":"e2e"}'
  if req "POST /api/compatibility/rules — создать правило" POST /api/compatibility/rules '200|201' "$body" @user; then
    rid=$(jqb '.id // empty')
    [[ -n $rid ]] && add_cleanup user "/api/compatibility/rules/$rid"
    req "DELETE /api/compatibility/rules/{id}" DELETE "/api/compatibility/rules/$rid" '200|204' "" @user
  fi

  if req "GET /api/compatibility/logs (до проверок)" GET /api/compatibility/logs 200 "" @user; then
    logs_before=$(jqb 'length')
  fi

  if [[ -z $C_CPU || -z $C_MB || -z $C_MB2 || -z $C_RAM || -z $C_CASE ]]; then
    skip "Проверки совместимости" "не созданы тестовые компоненты (раздел 5)"
  else
    compat_check "check socket: CPU ↔ MB-A (один сокет)"  "$C_CPU" "$C_MB"  socket     true
    compat_check "check socket: CPU ↔ MB-B (разные сокеты)" "$C_CPU" "$C_MB2" socket     false
    compat_check "check ramType: MB-A(DDR5) ↔ RAM(DDR5)"   "$C_MB"  "$C_RAM" ramType    true
    compat_check "check ramType: MB-B(DDR4) ↔ RAM(DDR5)"   "$C_MB2" "$C_RAM" ramType    false
    compat_check "check formFactor: MB-A(ATX) ↔ CASE(ATX)" "$C_MB"  "$C_CASE" formFactor true
    compat_check "check formFactor: MB-B(mATX) ↔ CASE(ATX)" "$C_MB2" "$C_CASE" formFactor false
    req_soft "check с несуществующим компонентом → 404" GET \
      "/api/compatibility/check?sourceId=999999999&targetId=$C_MB&fieldToMatch=socket" '404|400' '200|500' "" @user \
      "Feign-404 маскируется ProductClientFallback: клиент получает 200 + «Компонент временно недоступен», хотя компонента просто нет (compatibility-service/…/client/ProductClientFallback.java)." || true
  fi
  req "check без обязательных параметров → 400" GET "/api/compatibility/check?sourceId=1" 400 "" @user || true

  if [[ -n $logs_before ]] && req "GET /api/compatibility/logs (после проверок)" GET /api/compatibility/logs 200 "" @user; then
    logs_after=$(jqb 'length')
    checkj "Каждая проверка пишется в лог (было $logs_before → стало $logs_after)" "[$logs_before,$logs_after]" '.[1] > .[0]' \
      "CompatibilityCheckController сохраняет CompatibilityCheckLog в logRepository"
    checkw "  ↳ лог хранит категорию (а не имя поля) в targetCategory" \
      '[.[] | .targetCategory] | map(select(. == "socket" or . == "ramType" or . == "formFactor")) | length == 0' \
      "CompatibilityCheckController: log.setTargetCategory(fieldToMatch) пишет имя поля («socket») в колонку targetCategory — лог теряет смысл."
  fi
}

# ═════════════════════════════ 7. ЗАКАЗЫ И СКЛАД ═════════════════════════════
sec_orders() {
  section "7. Order-service: заказ, списание остатков, статусы"
  local c1 c2 body oid oid2

  req "GET /api/orders" GET /api/orders 200 "" @user || true
  if [[ -z $CAT_CPU ]]; then skip "Сценарий заказа" "нет категории CPU"; return; fi

  mk_component "E2E ORD-1 $E2E_ID" 1000 10 "$CAT_CPU" && c1=$MK_ID
  mk_component "E2E ORD-2 $E2E_ID" 250  20 "$CAT_CPU" && c2=$MK_ID
  if [[ -z $c1 || -z $c2 ]]; then skip "Сценарий заказа" "не созданы тестовые компоненты"; return; fi

  # Заказ №1: 3 шт. первого
  body=$(jq -nc --argjson u "$UID_N" --argjson c "$c1" '{userId:$u,items:[{componentId:$c,quantity:3}]}')
  if req "POST /api/orders — заказ: ORD-1 × 3" POST /api/orders '200|201' "$body" @user; then
    oid=$(jqb '.id // empty')
    check "  ↳ id, status=NEW, totalPrice=3000, userId=$UID_N, 1 позиция" \
      ".id != null and .status==\"NEW\" and .totalPrice==3000 and .userId==$UID_N and (.items|length)==1" \
      "OrderController.createOrder: total = Σ price × quantity (цена берётся через Feign getComponentById)"
    check "  ↳ items[0]: componentId=$c1, quantity=3, priceAtPurchase=1000" \
      ".items[0].componentId==$c1 and .items[0].quantity==3 and .items[0].priceAtPurchase==1000"
    # списание
    if req "GET компонента — остаток после заказа" GET "/api/components/$c1" 200 "" @user; then
      check "  ↳ СКЛАД: остаток 10 → 7 (списание через Feign decreaseStock)" '.stockQuantity==7' \
        "Остаток не изменился: order-service вызвал PATCH /components/{id}/decrease-stock, но списание не произошло. feign.circuitbreaker.enabled=true → любая ошибка Feign-вызова уходит в ProductClientFallback.decreaseStock(), который только пишет в stdout и заказ всё равно сохраняется."
      [[ $(jqb '.stockQuantity') == 10 ]] && root_cause stock-fallback "Остатки не списываются / перепродажа не блокируется: ProductClientFallback.decreaseStock() (order-service) глотает любую ошибку, а OrderController не проверяет остаток до сохранения заказа."
    fi
    req "GET /api/orders — заказ присутствует в списке" GET /api/orders 200 "" @user && \
      check "  ↳ заказ id=$oid найден" "any(.[]; .id==$oid)"
  fi

  # Заказ №2: две позиции
  body=$(jq -nc --argjson u "$UID_N" --argjson a "$c1" --argjson b "$c2" \
         '{userId:$u,items:[{componentId:$a,quantity:2},{componentId:$b,quantity:4}]}')
  if req "POST /api/orders — заказ из 2 позиций (ORD-1 ×2, ORD-2 ×4)" POST /api/orders '200|201' "$body" @user; then
    oid2=$(jqb '.id // empty')
    check "  ↳ totalPrice = 2×1000 + 4×250 = 3000, 2 позиции" '.totalPrice==3000 and (.items|length)==2'
    if req "GET компонента ORD-2 — остаток" GET "/api/components/$c2" 200 "" @user; then
      check "  ↳ СКЛАД: ORD-2 20 → 16" '.stockQuantity==16' "См. причину выше: списание Feign decreaseStock."
    fi
  fi

  # Перепродажа
  body=$(jq -nc --argjson u "$UID_N" --argjson c "$c1" '{userId:$u,items:[{componentId:$c,quantity:1000}]}')
  req_soft "Заказ на 1000 шт. при остатке < 1000 → отказ (4xx)" POST /api/orders '4[0-9][0-9]' '5[0-9][0-9]' "$body" @user \
    "Отказ есть, но как 5xx: product-service вернул 400, Feign → fallback → RuntimeException. Нужен явный 409/422 и проверка остатка ДО создания заказа." \
    "Заказ на 1000 шт. ПРИНЯТ при остатке ~5 (перепродажа). Причина: ProductClientFallback.decreaseStock() проглатывает 400 «мало на складе» от product-service." || \
    { [[ $HTTP_CODE =~ ^2 ]] && root_cause stock-fallback "Перепродажа не блокируется: ProductClientFallback.decreaseStock() (order-service) глотает любую ошибку, а OrderController не проверяет остаток до сохранения заказа."; }

  # Невалидные заказы
  body=$(jq -nc --argjson u "$UID_N" '{userId:$u,items:[{componentId:999999999,quantity:1}]}')
  req_soft "Заказ с несуществующим компонентом → 4xx" POST /api/orders '4[0-9][0-9]' '5[0-9][0-9]' "$body" @user \
    "Feign получает 404 → fallback бросает RuntimeException(\"Сервис продуктов недоступен…\") → клиенту 500 и вводящее в заблуждение сообщение (order-service/…/client/ProductClientFallback.java)." \
    "Заказ на несуществующий компонент создан!" || true
  body=$(jq -nc --argjson u "$UID_N" '{userId:$u}')
  req_soft "Заказ без items → 400" POST /api/orders '400|422' '500' "$body" @user \
    "OrderController.createOrder: request.getItems() == null → NullPointerException. Добавьте @Valid + @NotEmpty на OrderRequest.items." || true

  # Контроллеры, недоступные через Gateway
  if [[ -n $oid ]]; then
    req "GET /api/order-items/order/{id} — позиции заказа" GET "/api/order-items/order/$oid" 200 "" @user && \
      check "  ↳ 1 позиция" 'length==1'
    req "PATCH /api/status/{id}?status=PAID — смена статуса" PATCH "/api/status/$oid?status=PAID" 200 "" @user && \
      check "  ↳ status=PAID" '.status=="PAID"'
  fi

    # ─────────────── 7.5 КОРЗИНА ───────────────
    info "Проверка корзины (Cart API)"
    if [[ -n $USER_ID ]]; then
      # 1. Очищаем корзину перед тестом
      req "DELETE /api/cart/{userId}/clear — очистка корзины" DELETE "/api/cart/$USER_ID/clear" '200|204|404' "" @user || true

      # 2. Добавляем товар в корзину
      if req "POST /api/cart/add — добавить товар в корзину" POST "/api/cart/add?userId=$USER_ID&componentId=$c1&quantity=2" '200' "" @user; then
        # 3. Проверяем, что товар появился
        if req "GET /api/cart/{userId} — получить корзину" GET "/api/cart/$USER_ID" '200' "" @user; then
          check "  ↳ в корзине 1 товар" '(.items | length) == 1'
          check "  ↳ количество = 2" '.items[0].quantity == 2'
          check "  ↳ componentId = '"$c1"'" ".items[0].componentId == $c1"
        fi
      fi

      # 4. Очищаем корзину после теста
      req "DELETE /api/cart/{userId}/clear — очистка после теста" DELETE "/api/cart/$USER_ID/clear" '200|204' "" @user || true
    else
      skip "Тесты корзины" "нет ID пользователя (регистрация не удалась)"
    fi
}

# ═════════════════════════════ 8. АДМИН-ОПЕРАЦИИ ═════════════════════════════
sec_admin() {
  section "8. Admin-операции и права доступа"
  local body
  if [[ -z $USER_ID ]]; then skip "Admin-операции над тестовым пользователем" "id тестового пользователя неизвестен (регистрация не удалась)"; return; fi

  req_soft "IDOR: USER читает чужой профиль (/api/users/1) → 403" GET /api/users/1 '401|403' '200' "" @user \
    "UserController.getUserById не проверяет, что запрашивается свой профиль → любой пользователь видит email/роли других. Добавьте проверку владельца или ограничьте ADMIN." || true

  body='{"roles":["ROLE_USER"]}'
  if req "PUT /api/admin/users/{id}/roles — назначить роли" PUT "/api/admin/users/$USER_ID/roles" 200 "$body" @admin; then
    check "  ↳ roles == [ROLE_USER]" '.roles == ["ROLE_USER"]'
  fi
  req "PUT /api/admin/users/{id}/roles — несуществующая роль → 4xx" PUT "/api/admin/users/$USER_ID/roles" '400|404|422' '{"roles":["ROLE_NOPE"]}' @admin || \
    { [[ $HTTP_CODE == 500 ]] && hint "AdminController кидает RuntimeException(\"Role not found\") без @ControllerAdvice → 500 вместо 404."; }
  if req "DELETE /api/admin/users/{id} — удалить тестового пользователя" DELETE "/api/admin/users/$USER_ID" '200|204' "" @admin; then
    body=$(jq -nc --arg u "$USER_NAME" --arg p "$USER_PASS" '{username:$u,password:$p}')
    req "Логин удалённого пользователя → 4xx" POST /api/auth/login '400|401|403|404' "$body" || true
  fi
}

# ═════════════════════════════ ИТОГОВЫЙ ОТЧЁТ ════════════════════════════════
rep() { local i s=""; for (( i=0; i<$2; i++ )); do s+=$1; done; printf '%s' "$s"; }
report() {
  local total=$((PASS+FAIL+WARN+SKIP)) w=40 gp wp fp sp rest line
  (( total == 0 )) && total=1
  gp=$(( PASS*w/total )); wp=$(( WARN*w/total )); fp=$(( FAIL*w/total )); sp=$(( SKIP*w/total ))
  (( FAIL > 0 && fp == 0 )) && fp=1
  rest=$(( w-gp-wp-fp-sp )); (( rest > 0 )) && gp=$((gp+rest))

  printf '\n%s╔════════════════════════ ИТОГОВЫЙ ОТЧЁТ ════════════════════════╗%s\n' "$BOLD" "$N"
  printf '  Всего проверок   : %s%d%s\n' "$BOLD" "$((PASS+FAIL+WARN+SKIP))" "$N"
  printf '  %s✔ Пройдено      : %d%s\n' "$G" "$PASS" "$N"
  printf '  %s✘ Упало         : %d%s\n' "$R" "$FAIL" "$N"
  printf '  %s⚠ Предупреждений: %d%s\n' "$Y" "$WARN" "$N"
  printf '  %s⊘ Пропущено     : %d%s\n' "$D" "$SKIP" "$N"
  printf '  [%s%s%s%s%s%s%s%s%s]\n' "$G" "$(rep █ $gp)" "$Y" "$(rep █ $wp)" "$R" "$(rep █ $fp)" "$D" "$(rep ░ $sp)" "$N"

  if (( FAIL > 0 )); then
    printf '\n%s%s✘ Упавшие проверки:%s\n' "$BOLD" "$R" "$N"
    for line in "${FAIL_LIST[@]}"; do printf '  %s• %s%s\n' "$R" "$line" "$N"; done
  fi
  if (( WARN > 0 )); then
    printf '\n%s%s⚠ Предупреждения (не ломают сценарий, но это баги/риски):%s\n' "$BOLD" "$Y" "$N"
    for line in "${WARN_LIST[@]}"; do printf '  %s• %s%s\n' "$Y" "$line" "$N"; done
  fi
  if [[ -n $ROOT_CAUSES ]]; then
    printf '\n%s%s🔎 Вероятные первопричины (сгруппировано):%s\n' "$BOLD" "$C" "$N"
    printf '%s' "$ROOT_CAUSES" | sed 's/^<<[^>]*>>//' | while IFS= read -r line; do
      [[ -n $line ]] && printf '  %s▸ %s%s\n' "$C" "$line" "$N"
    done
  fi
  if (( JWT_BLOCKED && ! DIRECT )); then
    printf '\n%sℹ Gateway блокировал токены auth-service: часть тестов прошла на контрольном токене, а тесты auth-эндпоинтов за Gateway пропущены.%s\n' "$D" "$N"
  fi
  printf '\n'
  if (( FAIL > 0 )); then
    printf '%s%s  РЕЗУЛЬТАТ: ТЕСТЫ НЕ ПРОЙДЕНЫ  (%d из %d упали)%s\n\n' "$BOLD" "$R" "$FAIL" "$((PASS+FAIL+WARN+SKIP))" "$N"
  else
    printf '%s%s  РЕЗУЛЬТАТ: ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ%s%s\n\n' "$BOLD" "$G" "$([[ $WARN -gt 0 ]] && printf ' (есть предупреждения: %d)' "$WARN")" "$N"
  fi
}

# ═════════════════════════════════ MAIN ══════════════════════════════════════
printf '%s%sPC-Builder · E2E тесты бэкенда%s  %s(%s, %s)%s\n' "$BOLD" "$C" "$N" "$D" \
  "$( (( DIRECT )) && echo 'режим --direct: в обход Gateway' || echo "через Gateway $GATEWAY_URL")" "$(date '+%F %T')" "$N"

sec_infra
if (( GW_DOWN )); then
  printf '\n%s%s‼ API Gateway (%s) не отвечает — разделы 2–8 пропущены, они бессмысленны без Gateway.%s\n' "$BOLD" "$R" "$GATEWAY_URL" "$N"
  printf '%s  Поднимите сервисы (eureka → config → auth/product/order/compat → gateway) и повторите.\n  Для проверки бизнес-логики без Gateway: %s --direct%s\n' "$Y" "$0" "$N"
  if (( DIRECT )); then
    info "Режим --direct: бизнес-тесты идут напрямую в сервисы."
    sec_auth; sec_product; sec_compat; sec_orders; sec_admin
  fi
else
  sec_swagger
  sec_auth
  sec_security
  sec_product
  sec_compat
  sec_orders
  sec_admin
fi

cleanup_all
report
(( FAIL > 0 )) && exit 1
exit 0
