
package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogBuilderControllerTest {

    private CatalogController catalog;
    private BuilderController builder;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        catalog = new CatalogController(restTemplate);
        builder = new BuilderController(restTemplate);

        session = new MockHttpSession();
        model = new ExtendedModelMap();
    }

    private void login() {
        session.setAttribute("token", "test-token");
    }

    private void assertInvalidBuild(
            String name,
            List<Long> componentIds,
            List<Integer> quantities
    ) {
        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/builder",
                builder.saveBuild(
                        name,
                        componentIds,
                        quantities,
                        session,
                        redirect
                )
        );

        assertNotNull(
                redirect.getFlashAttributes().get("error"),
                "При невалидной сборке должно быть сообщение об ошибке"
        );
    }

    @Test
    void catalogRedirectsAnonymousUser() {
        assertEquals(
                "redirect:/login",
                catalog.catalog(
                        0,
                        "",
                        "name",
                        "asc",
                        session,
                        model
                )
        );
    }

    @Test
    void builderRedirectsAnonymousUser() {
        assertEquals(
                "redirect:/login",
                builder.builderPage(session, model)
        );
    }

    @Test
    void saveBuildRedirectsAnonymousUser() {
        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/login",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(1),
                        session,
                        redirect
                )
        );

        assertTrue(redirect.getFlashAttributes().isEmpty());
    }

    @Test
    void saveBuildRejectsBlankName() {
        login();

        assertInvalidBuild(
                "   ",
                List.of(1L),
                List.of(1)
        );
    }

    @Test
    void saveBuildRejectsEmptyComponents() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(),
                List.of()
        );
    }

    @Test
    void saveBuildRejectsMismatchedQuantities() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(1L, 2L),
                List.of(1)
        );
    }

    @Test
    void saveBuildRejectsZeroComponentId() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(0L),
                List.of(1)
        );
    }

    @Test
    void saveBuildRejectsNegativeComponentId() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(-1L),
                List.of(1)
        );
    }

    @Test
    void saveBuildRejectsZeroQuantity() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(1L),
                List.of(0)
        );
    }

    @Test
    void saveBuildRejectsNegativeQuantity() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(1L),
                List.of(-1)
        );
    }

    @Test
    void saveBuildRejectsQuantityAbove99() {
        login();

        assertInvalidBuild(
                "Gaming PC",
                List.of(1L),
                List.of(100)
        );
    }

    @Test
    void saveBuildRejectsMalformedJwt() {
        session.setAttribute("token", "invalid-token");

        RedirectAttributesModelMap redirect =
                new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/builder",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(1),
                        session,
                        redirect
                )
        );

        assertEquals(
                "Не удалось определить пользователя. "
                        + "Попробуйте войти в аккаунт заново.",
                redirect.getFlashAttributes().get("error")
        );
    }
}
