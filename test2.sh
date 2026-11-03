#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

GATEWAY="http://localhost:8080"
EUREKA="http://localhost:8761/eureka/apps"
PASSED=0
FAILED=0

echo -e "${YELLOW}=== НАЧАЛО ТЕСТИРОВАНИЯ БЭКЕНДА ===${NC}"

# 1. Проверка Eureka
echo -e "\n${YELLOW}[1] Проверка регистрации в Eureka...${NC}"
SERVICES=("AUTH-SERVICE" "PRODUCT-SERVICE" "COMPATIBILITY-SERVICE" "ORDER-SERVICE" "API-GATEWAY")
for SVC in "${SERVICES[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Accept: application/json" "$EUREKA/$SVC")
    if [ "$STATUS" -eq 200 ]; then
        echo -e "${GREEN}[OK]${NC} $SVC зарегистрирован"
        ((PASSED++))
    else
        echo -e "${RED}[FAIL]${NC} $SVC НЕ зарегистрирован (HTTP $STATUS)"
        ((FAILED++))
    fi
done

# 2. Проверка Swagger UI (через Gateway)
echo -e "\n${YELLOW}[2] Проверка Swagger UI...${NC}"
SWAGGER_URLS=("/v3/api-docs/auth" "/v3/api-docs/product" "/v3/api-docs/compatibility" "/v3/api-docs/order")
for URL in "${SWAGGER_URLS[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY$URL")
    if [ "$STATUS" -eq 200 ]; then
        echo -e "${GREEN}[OK]${NC} Swagger $URL доступен"
        ((PASSED++))
    else
        echo -e "${RED}[FAIL]${NC} Swagger $URL недоступен (HTTP $STATUS)"
        ((FAILED++))
    fi
done

# 3. Проверка безопасности (401 без токена)
echo -e "\n${YELLOW}[3] Проверка защиты эндпоинтов (401 без токена)...${NC}"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/components")
if [ "$STATUS" -eq 401 ]; then
    echo -e "${GREEN}[OK]${NC} Эндпоинт /api/components защищен (401)"
    ((PASSED++))
else
    echo -e "${RED}[FAIL]${NC} Эндпоинт /api/components вернул $STATUS (ожидался 401)"
    ((FAILED++))
fi

# 4. Авторизация (получение JWT)
echo -e "\n${YELLOW}[4] Авторизация...${NC}"
AUTH_RESPONSE=$(curl -s -X POST "$GATEWAY/api/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.token')

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
    echo -e "${GREEN}[OK]${NC} JWT получен (длина: ${#TOKEN} символов)"
    ((PASSED++))
else
    echo -e "${RED}[FAIL]${NC} Не удалось получить JWT. Ответ: $AUTH_RESPONSE"
    ((FAILED++))
    exit 1
fi

# 5. Проверка авторизованных эндпоинтов (200 с токеном)
echo -e "\n${YELLOW}[5] Проверка эндпоинтов с токеном...${NC}"
AUTH_HEADER="Authorization: Bearer $TOKEN"
ENDPOINTS=("/api/components" "/api/categories" "/api/builds" "/api/rules" "/api/orders" "/api/users/me")
for EP in "${ENDPOINTS[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$GATEWAY$EP")
    if [ "$STATUS" -eq 200 ]; then
        echo -e "${GREEN}[OK]${NC} GET $EP -> 200"
        ((PASSED++))
    else
        echo -e "${RED}[FAIL]${NC} GET $EP -> $STATUS (ожидался 200)"
        ((FAILED++))
    fi
done

# 6. Проверка создания сборки (POST)
echo -e "\n${YELLOW}[6] Проверка создания сборки...${NC}"
BUILD_RESPONSE=$(curl -s -X POST "$GATEWAY/api/builds" -H "$AUTH_HEADER" -H "Content-Type: application/json" -d '{"userId":1,"name":"Test Build","items":[{"componentId":1,"quantity":2}]}')
BUILD_TOTAL=$(echo "$BUILD_RESPONSE" | jq -r '.totalPrice')
if [ "$BUILD_TOTAL" == "50000" ]; then
    echo -e "${GREEN}[OK]${NC} Сборка создана, цена рассчитана верно ($BUILD_TOTAL)"
    ((PASSED++))
else
    echo -e "${RED}[FAIL]${NC} Ошибка при создании сборки. Ответ: $BUILD_RESPONSE"
    ((FAILED++))
fi

# 7. Проверка оформления заказа (Feign + уменьшение стока)
echo -e "\n${YELLOW}[7] Проверка оформления заказа и Feign...${NC}"
STOCK_BEFORE=$(curl -s -H "$AUTH_HEADER" "$GATEWAY/api/components/1" | jq -r '.stockQuantity')
ORDER_RESPONSE=$(curl -s -X POST "$GATEWAY/api/orders" -H "$AUTH_HEADER" -H "Content-Type: application/json" -d '{"userId":1,"items":[{"componentId":1,"quantity":1}]}')
STOCK_AFTER=$(curl -s -H "$AUTH_HEADER" "$GATEWAY/api/components/1" | jq -r '.stockQuantity')

if [ "$STOCK_BEFORE" -gt "$STOCK_AFTER" ]; then
    echo -e "${GREEN}[OK]${NC} Заказ создан, сток уменьшился ($STOCK_BEFORE -> $STOCK_AFTER)"
    ((PASSED++))
else
    echo -e "${RED}[FAIL]${NC} Заказ создан, но сток НЕ уменьшился ($STOCK_BEFORE -> $STOCK_AFTER)"
    ((FAILED++))
fi

# 8. Проверка совместимости (Compatibility)
echo -e "\n${YELLOW}[8] Проверка совместимости...${NC}"
COMPAT_RESPONSE=$(curl -s -H "$AUTH_HEADER" "$GATEWAY/api/compatibility/check?sourceId=1&targetId=3&fieldToMatch=socket")
IS_COMPATIBLE=$(echo "$COMPAT_RESPONSE" | jq -r '.compatible')
if [ "$IS_COMPATIBLE" == "true" ]; then
    echo -e "${GREEN}[OK]${NC} Совместимость проверена (socket AM5 = AM5)"
    ((PASSED++))
else
    echo -e "${RED}[FAIL]${NC} Ошибка проверки совместимости. Ответ: $COMPAT_RESPONSE"
    ((FAILED++))
fi

# Итог
echo -e "\n${YELLOW}=== РЕЗУЛЬТАТ ===${NC}"
echo -e "Успешно: ${GREEN}$PASSED${NC}"
echo -e "Ошибок:  ${RED}$FAILED${NC}"

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}ВСЕ ТЕСТЫ ПРОЙДЕНЫ! БЭКЕНД РАБОТАЕТ ИДЕАЛЬНО.${NC}"
else
    echo -e "${RED}ЕСТЬ ОШИБКИ. ПРОВЕРЬ ЛОГИ СЕРВИСОВ.${NC}"
fi
