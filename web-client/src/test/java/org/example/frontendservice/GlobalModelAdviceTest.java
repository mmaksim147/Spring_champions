package org.example.frontendservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GlobalModelAdviceTest {

    private static final String GATEWAY = "http://localhost:8080";

    private GlobalModelAdvice advice;
    private MockRestServiceServer server;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        advice = new GlobalModelAdvice(restTemplate);

        ReflectionTestUtils.setField(
                advice, "gatewayUrl", GATEWAY
        );

        server = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        session = new MockHttpSession();
        model = new ExtendedModelMap();
    }

    private void login(long userId) {
        session.setAttribute("token", "test-token");
        session.setAttribute("userId", userId);
    }

    private void expectCart(long userId, String response) {
        server.expect(requestTo(
                        GATEWAY + "/api/cart/" + userId
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(
                        header("Authorization", "Bearer test-token")
                )
                .andRespond(withSuccess(
                        response,
                        MediaType.APPLICATION_JSON
                ));
    }

    private void assertCartCount(int expected) {
        advice.addGlobalAttributes(session, model);
        assertEquals(expected, model.get("cartCount"));
        server.verify();
    }

    @Test
    void anonymousUserHasZeroCartCount() {
        assertCartCount(0);
    }

    @Test
    void anonymousUserWithUserIdStillHasZeroCount() {
        session.setAttribute("userId", 42L);

        assertCartCount(0);
    }

    @Test
    void emptyCartHasZeroCount() {
        login(42L);

        expectCart(42L, """
                {"id":1,"userId":42,"items":[]}
                """);

        assertCartCount(0);
    }

    @Test
    void cartWithOneItemHasCountOne() {
        login(42L);

        expectCart(42L, """
                {
                  "id":1,
                  "userId":42,
                  "items":[
                    {"id":10,"componentId":100,"quantity":1}
                  ]
                }
                """);

        assertCartCount(1);
    }

    @Test
    void cartWithThreeItemsHasCountThree() {
        login(42L);

        expectCart(42L, """
                {
                  "id":1,
                  "userId":42,
                  "items":[
                    {"id":10,"componentId":100,"quantity":1},
                    {"id":11,"componentId":200,"quantity":2},
                    {"id":12,"componentId":300,"quantity":3}
                  ]
                }
                """);

        assertCartCount(3);
    }

    @Test
    void countsCartLinesNotQuantitySum() {
        login(42L);

        expectCart(42L, """
                {
                  "items":[
                    {"componentId":100,"quantity":5},
                    {"componentId":200,"quantity":8}
                  ]
                }
                """);

        // Текущее поведение исходного кода:
        // items.size(), а не сумма quantity.
        assertCartCount(2);
    }

    @Test
    void nullItemsProduceZeroCount() {
        login(42L);

        expectCart(42L, """
                {"id":1,"userId":42,"items":null}
                """);

        assertCartCount(0);
    }

    @Test
    void missingItemsProduceZeroCount() {
        login(42L);

        expectCart(42L, """
                {"id":1,"userId":42}
                """);

        assertCartCount(0);
    }

    @Test
    void nullCartResponseProducesZeroCount() {
        login(42L);

        expectCart(42L, "null");

        assertCartCount(0);
    }

    @Test
    void serverErrorProducesZeroCount() {
        login(42L);

        server.expect(requestTo(GATEWAY + "/api/cart/42"))
                .andRespond(withServerError());

        assertCartCount(0);
    }

    @Test
    void missingCartProducesZeroCount() {
        login(42L);

        server.expect(requestTo(GATEWAY + "/api/cart/42"))
                .andRespond(withResourceNotFound());

        assertCartCount(0);
    }

    @Test
    void usesSessionUserIdInsteadOfFallback() {
        login(777L);

        expectCart(777L, """
                {"items":[{"componentId":5,"quantity":1}]}
                """);

        assertCartCount(1);
    }

    @Test
    void missingUserIdDoesNotRequestAnotherUsersCart() {
        session.setAttribute("token", "test-token");

        assertCartCount(0);
    }

    @Test
    void blankTokenDoesNotRequestCart() {
        session.setAttribute("token", "   ");
        session.setAttribute("userId", 42L);

        assertCartCount(0);
    }

    @Test
    void negativeUserIdDoesNotRequestCart() {
        session.setAttribute("token", "test-token");
        session.setAttribute("userId", -1L);

        assertCartCount(0);
    }

    @Test
    void invalidUserIdTypeDoesNotRequestCart() {
        session.setAttribute("token", "test-token");
        session.setAttribute("userId", "not-a-number");

        assertCartCount(0);
    }
}