
package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccessTest {

    private AdminUserController users;
    private AdminCategoryController categories;
    private RestTemplate restTemplate;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        users = new AdminUserController(restTemplate);
        categories = new AdminCategoryController(restTemplate);

        session = new MockHttpSession();
    }

    private void loginAsUser() {
        session.setAttribute("token", "user-token");
        session.setAttribute("roles", List.of("ROLE_USER"));
    }

    private void loginAsAdmin() {
        session.setAttribute("token", "admin-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));
    }

    @Test
    void anonymousCannotViewUsers() {
        assertEquals(
                "redirect:/login",
                users.list(session, new ExtendedModelMap())
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotViewUsers() {
        loginAsUser();

        assertEquals(
                "redirect:/catalog",
                users.list(session, new ExtendedModelMap())
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotDeleteUser() {
        loginAsUser();

        assertEquals(
                "redirect:/catalog",
                users.delete(10L, session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotChangeRole() {
        loginAsUser();

        assertEquals(
                "redirect:/catalog",
                users.changeRole(10L, "ADMIN", session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void anonymousCannotDeleteUser() {
        assertEquals(
                "redirect:/login",
                users.delete(10L, session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void anonymousCannotViewCategories() {
        assertEquals(
                "redirect:/login",
                categories.list(session, new ExtendedModelMap())
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotViewCategories() {
        loginAsUser();

        assertEquals(
                "redirect:/catalog",
                categories.list(session, new ExtendedModelMap())
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotCreateCategory() {
        loginAsUser();

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/catalog",
                categories.create("Processors", session, redirect)
        );

        assertFalse(redirect.containsAttribute("error"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotDeleteCategory() {
        loginAsUser();

        assertEquals(
                "redirect:/catalog",
                categories.delete(5L, session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void adminCannotCreateBlankCategory() {
        loginAsAdmin();

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/admin/categories",
                categories.create("   ", session, redirect)
        );

        assertEquals(
                "Название категории не может быть пустым",
                redirect.getAttribute("error")
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void adminCannotDeleteCategoryWithInvalidId() {
        loginAsAdmin();

        assertEquals(
                "redirect:/admin/categories?error",
                categories.delete(-1L, session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void adminCannotDeleteUserWithInvalidId() {
        loginAsAdmin();

        assertEquals(
                "redirect:/admin/users?error",
                users.delete(0L, session)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void adminCannotAssignUnknownRole() {
        loginAsAdmin();

        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(10L, "SUPERUSER", session)
        );
        verifyNoInteractions(restTemplate);
    }
}
