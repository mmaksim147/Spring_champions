package org.example.frontendservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogSearchPaginationTest {

    private RestTemplate restTemplate;
    private CatalogController controller;
    private MockHttpSession session;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        controller = new CatalogController(restTemplate);

        ReflectionTestUtils.setField(
                controller,
                "gatewayUrl",
                "http://gateway"
        );

        session = new MockHttpSession();
        session.setAttribute("token", "test-token");

        model = new ExtendedModelMap();

        // По умолчанию gateway возвращает пустую страницу.
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "content", List.of(),
                "totalPages", 0
        )));
    }

    private String openCatalog(
            int page,
            String search,
            String sort,
            String direction
    ) {
        return controller.catalog(
                page,
                search,
                sort,
                direction,
                session,
                model
        );
    }

    private void verifyGatewayUrl(String expectedUrl) {
        verify(restTemplate).exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        );
    }

    @Test
    void defaultCatalogLoadsFirstPage() {
        assertEquals(
                "catalog",
                openCatalog(0, "", "name", "asc")
        );

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=name,asc"
        );

        assertEquals(0, model.get("currentPage"));
        assertEquals(0, model.get("totalPages"));
    }

    @Test
    void searchIsSentToGateway() {
        assertEquals(
                "catalog",
                openCatalog(0, "RTX", "name", "asc")
        );

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=name,asc&search=RTX"
        );

        assertEquals("RTX", model.get("search"));
    }

    @Test
    void searchIsTrimmed() {
        openCatalog(0, "  Ryzen  ", "name", "asc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=name,asc&search=Ryzen"
        );

        assertEquals("Ryzen", model.get("search"));
    }

    @Test
    void paginationRequestsSecondPage() {
        openCatalog(1, "", "name", "asc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=1&size=12&sort=name,asc"
        );

        assertEquals(1, model.get("currentPage"));
    }

    @Test
    void searchAndPaginationWorkTogether() {
        openCatalog(2, "RTX", "name", "asc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=2&size=12&sort=name,asc&search=RTX"
        );

        assertEquals(2, model.get("currentPage"));
        assertEquals("RTX", model.get("search"));
    }

    @Test
    void priceDescendingSortIsSentToGateway() {
        openCatalog(0, "", "price", "desc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=price,desc"
        );

        assertEquals("price", model.get("sort"));
        assertEquals("desc", model.get("direction"));
    }

    @Test
    void invalidSortFallsBackToName() {
        openCatalog(0, "", "unknown", "asc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=name,asc"
        );

        assertEquals("name", model.get("sort"));
    }

    @Test
    void invalidDirectionFallsBackToAscending() {
        openCatalog(0, "", "price", "invalid");

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=price,asc"
        );

        assertEquals("asc", model.get("direction"));
    }

    @Test
    void negativePageFallsBackToFirstPage() {
        openCatalog(-5, "", "name", "asc");

        verifyGatewayUrl(
                "http://gateway/api/components?page=0&size=12&sort=name,asc"
        );

        assertEquals(0, model.get("currentPage"));
    }

    @Test
    void totalPagesAreReadFromGateway() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "content", List.of(),
                "totalPages", 5
        )));

        openCatalog(1, "", "name", "asc");

        assertEquals(5, model.get("totalPages"));
        assertEquals(1, model.get("currentPage"));
    }

    @Test
    void emptyResultsProduceEmptyProductList() {
        openCatalog(0, "nonexistent", "name", "asc");

        assertNotNull(model.get("products"));
        assertTrue(((List<?>) model.get("products")).isEmpty());
    }

    @Test
    void gatewayFailureProducesErrorMessage() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)
        )).thenThrow(new RuntimeException("Gateway unavailable"));

        assertEquals(
                "catalog",
                openCatalog(0, "", "name", "asc")
        );

        assertNotNull(model.get("error"));
        assertTrue(((List<?>) model.get("products")).isEmpty());
    }
}