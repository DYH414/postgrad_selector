package com.ruoyi.postgrad.rag.qdrant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

class QdrantCollectionServiceTest {

    @Test
    void shouldCreateCollectionWhenMissing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantCollectionService service = new QdrantCollectionService(restTemplate,
            "http://localhost:6333", "secret-key", "postgrad_rag_main", 1024);

        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("api-key", "secret-key"))
            .andRespond(withResourceNotFound());
        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(service::ensureCollection);
        server.verify();
    }

    @Test
    void shouldSurfaceNon404FailuresWhenEnsuringCollection() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantCollectionService service = new QdrantCollectionService(restTemplate,
            "http://localhost:6333", "", "postgrad_rag_main", 1024);

        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

        assertThrows(RestClientResponseException.class, service::ensureCollection);
        server.verify();
    }

    @Test
    void shouldClearCollectionByDroppingAndRecreatingCollection() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantCollectionService service = new QdrantCollectionService(restTemplate,
            "http://localhost:6333", "", "postgrad_rag_main", 1024);

        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().json("{\"vectors\":{\"size\":1024,\"distance\":\"Cosine\"}}"))
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        service.clearCollection();
        server.verify();
    }

    @Test
    void shouldReportReachability() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantCollectionService service = new QdrantCollectionService(restTemplate,
            "http://localhost:6333", "secret-key", "postgrad_rag_main", 1024);

        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("api-key", "secret-key"))
            .andRespond(withSuccess("{\"result\":{\"status\":\"green\"},\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertTrue(service.isReachable());
        server.verify();
    }

    @Test
    void shouldReturnFalseWhenReachabilityCheckFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QdrantCollectionService service = new QdrantCollectionService(restTemplate,
            "http://localhost:6333", "", "postgrad_rag_main", 1024);

        server.expect(requestTo("http://localhost:6333/collections/postgrad_rag_main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

        assertFalse(service.isReachable());
        server.verify();
    }
}
