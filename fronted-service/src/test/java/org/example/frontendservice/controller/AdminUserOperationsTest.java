package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AdminUserOperationsTest {

    private static final String GATEWAY = "http://localhost:8080";

    private AdminUserController controller;
    private MockRestServiceServer server;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        controller = new AdminUserController(restTemplate);
        ReflectionTestUtils.setField(
                controller, "gatewayUrl", GATEWAY
        );

        server = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        session = new MockHttpSession();
        session.setAttribute("token", "admin-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));

        model = new ExtendedModelMap();
    }

    @Test
    void loadsEmptyUserList() {
        server.expect(requestTo(GATEWAY + "/api/admin/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertEquals("admin/users", controller.list(session, model));
        assertEquals(List.of(), model.get("users"));

        server.verify();
    }

    @Test
    void loadsExistingUsers() {
        server.expect(requestTo(GATEWAY + "/api/admin/users"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"id":1,"username":"alice"},
                          {"id":2,"username":"bob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertEquals("admin/users", controller.list(session, model));

        List<?> users = (List<?>) model.get("users");

        assertNotNull(users);
        assertEquals(2, users.size());

        server.verify();
    }

    @Test
    void handlesUserListServerError() {
        server.expect(requestTo(GATEWAY + "/api/admin/users"))
                .andRespond(withServerError());

        assertEquals("admin/users", controller.list(session, model));
        assertEquals("Ошибка загрузки", model.get("error"));

        server.verify();
    }

    @Test
    void handlesUserListNotFound() {
        server.expect(requestTo(GATEWAY + "/api/admin/users"))
                .andRespond(withResourceNotFound());

        assertEquals("admin/users", controller.list(session, model));
        assertEquals("Ошибка загрузки", model.get("error"));

        server.verify();
    }

    @Test
    void deletesUserSuccessfully() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/admin/users",
                controller.delete(15L, session)
        );

        server.verify();
    }

    @Test
    void handlesDeleteServerError() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertEquals(
                "redirect:/admin/users?error",
                controller.delete(15L, session)
        );

        server.verify();
    }

    @Test
    void handlesDeleteNotFound() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/999"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withResourceNotFound());

        assertEquals(
                "redirect:/admin/users?error",
                controller.delete(999L, session)
        );

        server.verify();
    }

    @Test
    void assignsAdminRole() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15/roles"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(
                        header("Content-Type", "application/json")
                )
                .andExpect(
                        jsonPath("$.roles[0]").value("ROLE_ADMIN")
                )
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/admin/users",
                controller.changeRole(15L, "ADMIN", session)
        );

        server.verify();
    }

    @Test
    void assignsUserRole() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15/roles"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(
                        jsonPath("$.roles[0]").value("ROLE_USER")
                )
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/admin/users",
                controller.changeRole(15L, "USER", session)
        );

        server.verify();
    }

    @Test
    void acceptsAlreadyPrefixedRole() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15/roles"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(
                        jsonPath("$.roles[0]").value("ROLE_ADMIN")
                )
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/admin/users",
                controller.changeRole(15L, "ROLE_ADMIN", session)
        );

        server.verify();
    }

    @Test
    void handlesRoleUpdateServerError() {
        server.expect(requestTo(GATEWAY + "/api/admin/users/15/roles"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertEquals(
                "redirect:/admin/users?error",
                controller.changeRole(15L, "ADMIN", session)
        );

        server.verify();
    }

    @Test
    void rejectsBlankRoleWithoutGatewayRequest() {
        assertEquals(
                "redirect:/admin/users?error",
                controller.changeRole(15L, "   ", session)
        );

        server.verify();
    }
}