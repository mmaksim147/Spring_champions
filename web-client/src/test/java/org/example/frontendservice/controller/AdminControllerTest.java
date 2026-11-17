
package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminControllerTest {

    private AdminController controller;
    private MockHttpSession session;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        controller = new AdminController(restTemplate);
        session = new MockHttpSession();
    }

    @Test
    void anonymousUserRedirectedToLogin() {
        assertEquals(
                "redirect:/login",
                controller.adminPage(session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserRedirectedToCatalog() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_USER"));

        assertEquals(
                "redirect:/catalog",
                controller.adminPage(session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void administratorCanOpenAdminPage() {
        session.setAttribute("token", "test-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));

        assertEquals(
                "admin/index",
                controller.adminPage(session)
        );

        verifyNoInteractions(restTemplate);
    }
}
