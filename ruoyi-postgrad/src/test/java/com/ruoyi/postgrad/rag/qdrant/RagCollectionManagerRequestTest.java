package com.ruoyi.postgrad.rag.qdrant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RagCollectionManagerRequestTest {

    private static final String QDRANT_URL = "http://localhost:6333";

    @Test
    void shouldUseQdrantFieldSchemaWhenCreatingPayloadIndexes() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RagCollectionManager manager = new RagCollectionManager(restTemplate, QDRANT_URL);
        String collection = "postgrad_rag_candidate";

        for (String field : new String[] {
            "source_type", "school_id", "school_name", "program_id", "year", "doc_type", "section"
        }) {
            server.expect(requestTo(QDRANT_URL + "/collections/" + collection + "/index"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"field_name\":\"" + field
                    + "\",\"field_schema\":\"keyword\"}"))
                .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}",
                    MediaType.APPLICATION_JSON));
        }

        manager.createPayloadIndexes(collection);

        server.verify();
    }

    @Test
    void shouldCreateAliasWithoutDeleteWhenNoAliasExists() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RagCollectionManager manager = new RagCollectionManager(restTemplate, QDRANT_URL);

        server.expect(requestTo(QDRANT_URL + "/aliases"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"result\":{\"aliases\":[]},\"status\":\"ok\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(QDRANT_URL + "/collections/aliases"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                String body = request.getBody().toString();
                assertTrue(body.contains("create_alias"));
                assertFalse(body.contains("delete_alias"));
            })
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}",
                MediaType.APPLICATION_JSON));

        manager.switchAlias("postgrad_rag_active", "postgrad_rag_v1");

        server.verify();
    }

    @Test
    void shouldDeleteAndCreateAliasInOneAtomicSwitchRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RagCollectionManager manager = new RagCollectionManager(restTemplate, QDRANT_URL);

        server.expect(requestTo(QDRANT_URL + "/aliases"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"result\":{\"aliases\":[{\"alias_name\":\"postgrad_rag_active\","
                    + "\"collection_name\":\"postgrad_rag_v1\"}]},\"status\":\"ok\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(QDRANT_URL + "/collections/aliases"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                String body = request.getBody().toString();
                int deleteAt = body.indexOf("delete_alias");
                int createAt = body.indexOf("create_alias");
                assertTrue(deleteAt >= 0);
                assertTrue(createAt > deleteAt);
            })
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}",
                MediaType.APPLICATION_JSON));

        manager.switchAlias("postgrad_rag_active", "postgrad_rag_v2");

        server.verify();
    }

    @Test
    void shouldAuthenticateCollectionLifecycleRequests() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RagCollectionManager manager = new RagCollectionManager(
            restTemplate, QDRANT_URL, "secret-key");

        server.expect(requestTo(QDRANT_URL + "/collections/postgrad_rag_candidate"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header("api-key", "secret-key"))
            .andRespond(withSuccess("{\"result\":true,\"status\":\"ok\"}",
                MediaType.APPLICATION_JSON));

        manager.deleteCollection("postgrad_rag_candidate");

        server.verify();
    }
}
