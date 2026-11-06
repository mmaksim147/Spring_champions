
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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AdminCategoryOperationsTest {

    private static final String GATEWAY = "http://localhost:8080";

    private AdminCategoryController controller;
    private MockRestServiceServer server;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        controller = new AdminCategoryController(restTemplate);

        ReflectionTestUtils.setField(
                controller,
                "gatewayUrl",
                GATEWAY
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
    void loadsEmptyCategories() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertEquals(
                "admin/categories",
                controller.list(session, model)
        );

        assertEquals(List.of(), model.get("categories"));
        server.verify();
    }

    @Test
    void loadsExistingCategories() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        [
                          {"id":1,"name":"CPU"},
                          {"id":2,"name":"GPU"},
                          {"id":3,"name":"RAM"}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "admin/categories",
                controller.list(session, model)
        );

        List<?> categories = (List<?>) model.get("categories");

        assertNotNull(categories);
        assertEquals(3, categories.size());

        server.verify();
    }

    @Test
    void handlesNullCategoryResponse() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andRespond(withSuccess(
                        "null",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "admin/categories",
                controller.list(session, model)
        );

        assertEquals(List.of(), model.get("categories"));
        server.verify();
    }

    @Test
    void handlesCategoryListServerError() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andRespond(withServerError());

        assertEquals(
                "admin/categories",
                controller.list(session, model)
        );

        assertEquals("Ошибка загрузки", model.get("error"));
        server.verify();
    }

    @Test
    void createsCategorySuccessfully() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.name").value("Processors"))
                .andRespond(withSuccess());

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/admin/categories",
                controller.create("Processors", session, redirect)
        );

        assertFalse(redirect.containsAttribute("error"));
        server.verify();
    }

    @Test
    void trimsCategoryNameBeforeSending() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        jsonPath("$.name").value("Graphics Cards")
                )
                .andRespond(withSuccess());

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/admin/categories",
                controller.create("  Graphics Cards  ", session, redirect)
        );

        assertFalse(redirect.containsAttribute("error"));
        server.verify();
    }

    @Test
    void handlesCreateServerError() {
        server.expect(requestTo(GATEWAY + "/api/categories"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/admin/categories",
                controller.create("Processors", session, redirect)
        );

        assertEquals(
                "Не удалось добавить категорию. Возможно, она уже существует.",
                redirect.getAttribute("error")
        );

        server.verify();
    }

    @Test
    void rejectsNullCategoryName() {
        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/admin/categories",
                controller.create(null, session, redirect)
        );

        assertEquals(
                "Название категории не может быть пустым",
                redirect.getAttribute("error")
        );

        server.verify();
    }

    @Test
    void deletesCategorySuccessfully() {
        server.expect(requestTo(GATEWAY + "/api/categories/5"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/admin/categories",
                controller.delete(5L, session)
        );

        server.verify();
    }

    @Test
    void handlesDeleteServerError() {
        server.expect(requestTo(GATEWAY + "/api/categories/5"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertEquals(
                "redirect:/admin/categories?error",
                controller.delete(5L, session)
        );

        server.verify();
    }

    @Test
    void handlesDeleteNotFound() {
        server.expect(requestTo(GATEWAY + "/api/categories/999"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withResourceNotFound());

        assertEquals(
                "redirect:/admin/categories?error",
                controller.delete(999L, session)
        );

        server.verify();
    }

    @Test
    void rejectsNullCategoryId() {
        assertEquals(
                "redirect:/admin/categories?error",
                controller.delete(null, session)
        );

        server.verify();
    }
}
