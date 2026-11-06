
package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminValidationTest {

    private RestTemplate restTemplate;

    private AdminUserController users;
    private AdminCategoryController categories;
    private AdminController admin;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        users = new AdminUserController(restTemplate);
        categories = new AdminCategoryController(restTemplate);
        admin = new AdminController(restTemplate);

        session = new MockHttpSession();
        session.setAttribute("token", "admin-token");
        session.setAttribute("roles", List.of("ROLE_ADMIN"));
    }

    @Test
    void rejectsZeroUserIdOnDelete() {
        assertEquals(
                "redirect:/admin/users?error",
                users.delete(0L, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsNegativeUserIdOnDelete() {
        assertEquals(
                "redirect:/admin/users?error",
                users.delete(-5L, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsNullUserIdOnDelete() {
        assertEquals(
                "redirect:/admin/users?error",
                users.delete(null, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsZeroUserIdOnRoleChange() {
        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(0L, "ADMIN", session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsNegativeUserIdOnRoleChange() {
        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(-10L, "USER", session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsUnsupportedRole() {
        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(10L, "SUPERADMIN", session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsUnsupportedPrefixedRole() {
        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(10L, "ROLE_MANAGER", session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsNullRole() {
        assertEquals(
                "redirect:/admin/users?error",
                users.changeRole(10L, null, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsBlankCategoryName() {
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
    void rejectsZeroCategoryId() {
        assertEquals(
                "redirect:/admin/categories?error",
                categories.delete(0L, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsNegativeCategoryId() {
        assertEquals(
                "redirect:/admin/categories?error",
                categories.delete(-1L, session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void emptyTokenCannotAccessUserList() {
        session.setAttribute("token", "");

        assertEquals(
                "redirect:/catalog",
                users.list(session, new ExtendedModelMap())
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void fakeAdminRoleCannotAccessAdminPage() {
        session.setAttribute(
                "roles",
                List.of("ROLE_ADMINISTRATOR")
        );

        assertEquals(
                "redirect:/catalog",
                admin.adminPage(session)
        );

        verifyNoInteractions(restTemplate);
    }

    @Test
    void regularUserCannotCreateCategory() {
        session.setAttribute(
                "roles",
                List.of("ROLE_USER")
        );

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/catalog",
                categories.create("Processors", session, redirect)
        );

        assertFalse(redirect.containsAttribute("error"));
        verifyNoInteractions(restTemplate);
    }
}
