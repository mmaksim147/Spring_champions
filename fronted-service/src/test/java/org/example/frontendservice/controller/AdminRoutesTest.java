package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

class AdminRoutesTest {

    private static final String GATEWAY = "http://localhost:8080";

    private MockMvc mvc;
    private MockRestServiceServer server;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        AdminController adminController =
                new AdminController(restTemplate);

        AdminUserController userController =
                new AdminUserController(restTemplate);

        ReflectionTestUtils.setField(
                userController, "gatewayUrl", GATEWAY
        );

        // При конфликте маршрутов создание MockMvc
        // выбросит исключение.
        mvc = MockMvcBuilders.standaloneSetup(
                adminController,
                userController
        ).build();

        server = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        session = new MockHttpSession();
        session.setAttribute("token", "admin-token");
        session.setAttribute(
                "roles", List.of("ROLE_ADMIN")
        );
    }

    @Test
    void adminPageRouteWorks() throws Exception {
        mvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"));

        server.verify();
    }

    @Test
    void firstDeleteRouteWorks() throws Exception {
        server.expect(requestTo(
                        GATEWAY + "/api/admin/users/10"
                ))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        mvc.perform(
                        post("/admin/users/delete/10")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/admin/users")
                );

        server.verify();
    }

    @Test
    void alternativeDeleteRouteWorks() throws Exception {
        server.expect(requestTo(
                        GATEWAY + "/api/admin/users/10"
                ))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        mvc.perform(
                        post("/admin/users/10/delete")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/admin/users")
                );

        server.verify();
    }

    @Test
    void firstRoleRouteWorks() throws Exception {
        server.expect(requestTo(
                        GATEWAY + "/api/admin/users/10/roles"
                ))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(
                        jsonPath("$.roles[0]")
                                .value("ROLE_ADMIN")
                )
                .andRespond(withSuccess());

        mvc.perform(
                        post("/admin/users/role/10")
                                .param("role", "ADMIN")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/admin/users")
                );

        server.verify();
    }

    @Test
    void alternativeRoleRouteWorks() throws Exception {
        server.expect(requestTo(
                        GATEWAY + "/api/admin/users/10/roles"
                ))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(
                        jsonPath("$.roles[0]")
                                .value("ROLE_USER")
                )
                .andRespond(withSuccess());

        mvc.perform(
                        post("/admin/users/10/role")
                                .param("role", "USER")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/admin/users")
                );

        server.verify();
    }

    @Test
    void regularUserCannotDeleteThroughHttp() throws Exception {
        session.setAttribute(
                "roles", List.of("ROLE_USER")
        );

        mvc.perform(
                        post("/admin/users/delete/10")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/catalog")
                );

        server.verify();
    }

    @Test
    void regularUserCannotChangeRoleThroughHttp()
            throws Exception {

        session.setAttribute(
                "roles", List.of("ROLE_USER")
        );

        mvc.perform(
                        post("/admin/users/role/10")
                                .param("role", "ADMIN")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("/catalog")
                );

        server.verify();
    }
}