
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BuildsControllerTest {

    private static final String GATEWAY = "http://localhost:8080";

    private BuildsController controller;
    private MockRestServiceServer server;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();

        controller = new BuildsController(restTemplate);

        ReflectionTestUtils.setField(
                controller,
                "gatewayUrl",
                GATEWAY
        );

        server = MockRestServiceServer
                .bindTo(restTemplate)
                .build();

        session = new MockHttpSession();
        model = new ExtendedModelMap();
    }

    private String jwt(long userId) {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("{\"userId\":" + userId + "}")
                                .getBytes(StandardCharsets.UTF_8)
                );

        return "header." + payload + ".signature";
    }

    private void login() {
        session.setAttribute("token", jwt(42));
    }

    @Test
    void anonymousUserCannotViewBuilds() {
        assertEquals(
                "redirect:/login",
                controller.listBuilds(session, model)
        );

        server.verify();
    }

    @Test
    void anonymousUserCannotCheckoutBuild() {
        assertEquals(
                "redirect:/login",
                controller.checkoutBuild(15L, session)
        );

        server.verify();
    }

    @Test
    void buildsPageLoadsEmptyList() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds?userId=42"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "Authorization",
                        "Bearer " + jwt(42)
                ))
                .andRespond(withSuccess(
                        "[]",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "builds",
                controller.listBuilds(session, model)
        );

        assertEquals(
                List.of(),
                model.get("builds")
        );

        server.verify();
    }

    @Test
    void buildsPageLoadsExistingBuilds() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds?userId=42"
                ))
                .andRespond(withSuccess(
                        "[{\"id\":15,\"name\":\"Gaming PC\"}]",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                "builds",
                controller.listBuilds(session, model)
        );

        List<?> builds = (List<?>) model.get("builds");

        assertNotNull(builds);
        assertEquals(1, builds.size());

        server.verify();
    }

    @Test
    void buildsPageHandlesGatewayError() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds?userId=42"
                ))
                .andRespond(withServerError());

        assertEquals(
                "builds",
                controller.listBuilds(session, model)
        );

        assertEquals(
                "Ошибка загрузки сборок",
                model.get("error")
        );

        server.verify();
    }

    @Test
    void checkoutBuildCreatesOrderAndDeletesBuild() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds/15"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "id": 15,
                          "userId": 42,
                          "items": [
                            {
                              "component": {"id": 100},
                              "quantity": 2
                            },
                            {
                              "component": {"id": 200},
                              "quantity": 1
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        server.expect(requestTo(
                        GATEWAY + "/api/orders"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(
                        jsonPath("$.items[0].componentId")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.items[1].componentId")
                                .value(200)
                )
                .andExpect(
                        jsonPath("$.items[1].quantity")
                                .value(1)
                )
                .andRespond(withSuccess());

        server.expect(requestTo(
                        GATEWAY + "/api/builds/15"
                ))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        assertEquals(
                "redirect:/orders?success",
                controller.checkoutBuild(15L, session)
        );

        server.verify();
    }

    @Test
    void checkoutBuildHandlesMissingBuild() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds/999"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        String result = controller.checkoutBuild(
                999L,
                session
        );

        assertTrue(
                result.startsWith("redirect:/builds?error=")
        );

        server.verify();
    }

    @Test
    void checkoutBuildDoesNotDeleteWhenOrderFails() {
        login();

        server.expect(requestTo(
                        GATEWAY + "/api/builds/15"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "id": 15,
                          "userId": 42,
                          "items": [
                            {
                              "component": {"id": 100},
                              "quantity": 1
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        server.expect(requestTo(
                        GATEWAY + "/api/orders"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String result = controller.checkoutBuild(
                15L,
                session
        );

        assertTrue(
                result.startsWith("redirect:/builds?error=")
        );

        // При ошибке создания заказа DELETE не вызывается.
        server.verify();
    }
}
