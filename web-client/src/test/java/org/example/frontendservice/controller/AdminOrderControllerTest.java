package org.example.frontendservice.controller;

import org.example.frontendservice.dto.OrderDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOrderControllerTest {

    private RestTemplate restTemplate;
    private AdminOrderController controller;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        controller = new AdminOrderController(restTemplate);

        ReflectionTestUtils.setField(
                controller,
                "gatewayUrl",
                "http://gateway"
        );

        session = new MockHttpSession();
        model = new ExtendedModelMap();
    }

    @Test
    void anonymousUserCannotOpenOrders() {
        assertEquals(
                "redirect:/login",
                controller.listOrders(session, model)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotOpenOrders() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_USER"));

        assertEquals(
                "redirect:/catalog",
                controller.listOrders(session, model)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void adminCanLoadOrders() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));

        OrderDto order = new OrderDto();
        order.setId(15L);
        order.setUserId(3L);
        order.setStatus("NEW");

        when(restTemplate.exchange(
                eq("http://gateway/api/orders"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(OrderDto[].class)
        )).thenReturn(ResponseEntity.ok(new OrderDto[]{order}));

        assertEquals(
                "admin/orders",
                controller.listOrders(session, model)
        );

        @SuppressWarnings("unchecked")
        List<OrderDto> orders =
                (List<OrderDto>) model.get("orders");

        assertNotNull(orders);
        assertEquals(1, orders.size());
        assertEquals(15L, orders.get(0).getId());
    }

    @Test
    void emptyBackendResponseProducesEmptyList() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));

        when(restTemplate.exchange(
                eq("http://gateway/api/orders"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(OrderDto[].class)
        )).thenReturn(ResponseEntity.ok(new OrderDto[0]));

        assertEquals(
                "admin/orders",
                controller.listOrders(session, model)
        );

        assertEquals(List.of(), model.get("orders"));
    }

    @Test
    void backendFailureShowsError() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));

        when(restTemplate.exchange(
                eq("http://gateway/api/orders"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(OrderDto[].class)
        )).thenThrow(new RuntimeException("Backend unavailable"));

        assertEquals(
                "admin/orders",
                controller.listOrders(session, model)
        );

        assertNotNull(model.get("error"));
        assertEquals(List.of(), model.get("orders"));
    }
}