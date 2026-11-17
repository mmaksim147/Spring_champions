
package org.example.frontendservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OrderControllerTest {

    private static final String GATEWAY = "http://localhost:8080";
    private static final long USER_ID = 42L;

    private OrderController controller;
    private MockRestServiceServer server;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        controller = new OrderController(
                restTemplate,
                new ObjectMapper()
        );

        ReflectionTestUtils.setField(
                controller,
                "gatewayUrl",
                GATEWAY
        );

        server = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        session = new MockHttpSession();
        model = new ExtendedModelMap();
    }

    private String jwt(long userId) {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("{\"userId\":" + userId + "}")
                                .getBytes(StandardCharsets.UTF_8)
                );

        return "header." + payload + ".signature";
    }

    private String login() {
        String token = jwt(USER_ID);
        session.setAttribute("token", token);
        return token;
    }

    @Test
    void anonymousUserCannotOpenOrders() {
        assertEquals(
                "redirect:/login",
                controller.ordersPage(session, model)
        );

        server.verify();
    }

    @Test
    void anonymousUserCannotCreateOrder() {
        assertEquals(
                "redirect:/login",
                controller.createOrder(10L, 2, session)
        );

        server.verify();
    }

    @Test
    void ordersPageLoadsEmptyOrders() {
        String token = login();

        server.expect(requestTo(
                        GATEWAY + "/api/orders/user/" + USER_ID
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andRespond(withSuccess(
                        "[]",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "orders",
                controller.ordersPage(session, model)
        );

        Object orders = model.get("orders");

        assertNotNull(orders);
        assertTrue(((List<?>) orders).isEmpty());

        server.verify();
    }

    @Test
    void ordersPageLoadsExistingOrders() {
        String token = login();

        String response = """
                [
                  {
                    "id": 15,
                    "userId": 42,
                    "status": "NEW",
                    "totalPrice": 30000.00,
                    "createdAt": "2026-11-06T10:51:35",
                    "items": []
                  }
                ]
                """;

        server.expect(requestTo(
                        GATEWAY + "/api/orders/user/" + USER_ID
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andRespond(withSuccess(
                        response,
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "orders",
                controller.ordersPage(session, model)
        );

        Object orders = model.get("orders");

        assertNotNull(orders);
        assertEquals(1, ((List<?>) orders).size());

        server.verify();
    }

    @Test
    void ordersPageLoadsComponentNames() {
        login();

        String response = """
                [
                  {
                    "id": 15,
                    "userId": 42,
                    "status": "NEW",
                    "totalPrice": 15000.00,
                    "createdAt": "2026-11-06T10:51:35",
                    "items": [
                      {
                        "id": 100,
                        "componentId": 3,
                        "quantity": 1,
                        "priceAtPurchase": 15000.00
                      }
                    ]
                  }
                ]
                """;

        server.expect(requestTo(
                        GATEWAY + "/api/orders/user/" + USER_ID
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        response,
                        MediaType.APPLICATION_JSON
                ));

        server.expect(requestTo(
                        GATEWAY + "/api/components/3"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "id": 3,
                          "name": "Тестовый процессор",
                          "price": 15000.00
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "orders",
                controller.ordersPage(session, model)
        );

        assertNotNull(model.get("orders"));
        assertNotNull(model.get("componentNames"));

        server.verify();
    }

    @Test
    void ordersPageHandlesGatewayError() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/orders/user/" + USER_ID
                ))
                .andRespond(withServerError());

        assertEquals(
                "orders",
                controller.ordersPage(session, model)
        );

        assertNotNull(model.get("error"));

        server.verify();
    }

    @Test
    void createOrderSendsCorrectRequest() {
        String token = login();

        server.expect(requestTo(
                        GATEWAY + "/api/orders"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(
                        jsonPath("$.items[0].componentId")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andRespond(withSuccess(
                        """
                        {
                          "id": 99,
                          "userId": 42,
                          "status": "NEW",
                          "totalPrice": 30000.00,
                          "items": []
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "redirect:/orders?success",
                controller.createOrder(10L, 2, session)
        );

        server.verify();
    }

    @Test
    void createOrderHandlesGatewayError() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/orders"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertEquals(
                "redirect:/catalog?error",
                controller.createOrder(10L, 2, session)
        );

        server.verify();
    }

    @Test
    void ordersPageUsesCurrentUserId() {
        String token = jwt(77L);
        session.setAttribute("token", token);

        server.expect(requestTo(
                        GATEWAY + "/api/orders/user/77"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andRespond(withSuccess(
                        "[]",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "orders",
                controller.ordersPage(session, model)
        );

        server.verify();
    }
}
