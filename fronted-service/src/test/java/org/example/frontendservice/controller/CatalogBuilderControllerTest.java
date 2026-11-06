package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void catalogRedirectsAnonymousUser() {
        assertEquals(
                "redirect:/login",
                catalog.catalog(0, "", "name", "asc", session, model)
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
        assertEquals(
                "redirect:/login",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsBlankName() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "   ",
                        List.of(1L),
                        List.of(1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsEmptyComponents() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(),
                        List.of(),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsMismatchedQuantities() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L, 2L),
                        List.of(1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsZeroComponentId() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(0L),
                        List.of(1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsNegativeComponentId() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(-1L),
                        List.of(1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsZeroQuantity() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(0),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsNegativeQuantity() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(-1),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsQuantityAbove99() {
        session.setAttribute("token", "test-token");

        assertEquals(
                "redirect:/builder?error=invalid",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(100),
                        session
                )
        );
    }

    @Test
    void saveBuildRejectsMalformedJwt() {
        session.setAttribute("token", "invalid-token");

        assertEquals(
                "redirect:/builder?error=user",
                builder.saveBuild(
                        "Gaming PC",
                        List.of(1L),
                        List.of(1),
                        session
                )
        );
    }
}