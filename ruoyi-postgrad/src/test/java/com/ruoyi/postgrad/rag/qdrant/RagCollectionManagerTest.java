package com.ruoyi.postgrad.rag.qdrant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

class RagCollectionManagerTest {

    private static final String QDRANT_URL = "http://localhost:6333";
    private static final String TEST_ALIAS = "postgrad_rag_test_alias";

    private RestTemplate restTemplate;
    private RagCollectionManager manager;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        manager = new RagCollectionManager(restTemplate, QDRANT_URL);
        cleanupTestArtifacts();
    }

    @Test
    @SuppressWarnings("unchecked")
    void T1_1_createVersionCollectionAndPayloadIndexes() {
        int dimension = 1024;
        String collectionName = manager.createVersionCollection(dimension);
        assertNotNull(collectionName);
        assertTrue(collectionName.startsWith("postgrad_rag_"));

        manager.createPayloadIndexes(collectionName);

        // Verify collection exists with correct vector config
        Map<String, Object> info = restTemplate.getForObject(
            QDRANT_URL + "/collections/" + collectionName, Map.class);
        assertNotNull(info);
        assertEquals("ok", info.get("status"));

        Map<String, Object> result = (Map<String, Object>) info.get("result");
        assertNotNull(result);

        Map<String, Object> config = (Map<String, Object>) result.get("config");
        assertNotNull(config);

        Map<String, Object> params = (Map<String, Object>) config.get("params");
        assertNotNull(params);

        Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
        assertNotNull(vectors);
        assertEquals(dimension, vectors.get("size"));
        assertEquals("Cosine", vectors.get("distance"));

        // Clean up
        manager.deleteCollection(collectionName);
    }

    @Test
    void T1_2_switchAliasAndResolve() {
        String collectionName = manager.createVersionCollection(1024);

        manager.switchAlias(TEST_ALIAS, collectionName);

        String resolved = manager.resolveAlias(TEST_ALIAS);
        assertEquals(collectionName, resolved);

        // Clean up
        deleteAlias(TEST_ALIAS);
        manager.deleteCollection(collectionName);
    }

    @Test
    void T1_3_switchAliasFromV1ToV2() {
        String v1 = manager.createVersionCollection(1024);
        String v2 = manager.createVersionCollection(1024);

        manager.switchAlias(TEST_ALIAS, v1);
        assertEquals(v1, manager.resolveAlias(TEST_ALIAS));

        manager.switchAlias(TEST_ALIAS, v2);
        assertEquals(v2, manager.resolveAlias(TEST_ALIAS));

        // Clean up
        deleteAlias(TEST_ALIAS);
        manager.deleteCollection(v1);
        manager.deleteCollection(v2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void T1_4_getPointCount() {
        String collectionName = manager.createVersionCollection(1024);

        long count = manager.getPointCount(collectionName);
        assertEquals(0L, count);

        // Also verify via direct API
        Map<String, Object> info = restTemplate.getForObject(
            QDRANT_URL + "/collections/" + collectionName, Map.class);
        Map<String, Object> result = (Map<String, Object>) info.get("result");
        assertEquals(0, result.get("points_count"));

        manager.deleteCollection(collectionName);
    }

    @Test
    void T1_5_deleteCollection() {
        String collectionName = manager.createVersionCollection(1024);

        manager.deleteCollection(collectionName);

        // Verify collection is gone — expect 404 or non-ok status
        try {
            Map<String, Object> info = restTemplate.getForObject(
                QDRANT_URL + "/collections/" + collectionName, Map.class);
            if (info != null) {
                assertNotEquals("ok", info.get("status"));
            }
        } catch (Exception e) {
            // 404 Not Found is expected and acceptable
        }
    }

    @Test
    void T1_6_resolveAliasNonexistent() {
        String result = manager.resolveAlias("postgrad_rag_nonexistent_alias_" + UUID.randomUUID());
        assertNull(result);
    }

    // ---- helpers ----

    private void cleanupTestArtifacts() {
        deleteAliasSafely(TEST_ALIAS);
        deleteLeftoverVersionedCollections();
    }

    private void deleteAliasSafely(String alias) {
        try {
            deleteAlias(alias);
        } catch (Exception ignored) {
        }
    }

    private void deleteAlias(String alias) {
        Map<String, Object> deleteAction = new LinkedHashMap<>();
        Map<String, String> config = new LinkedHashMap<>();
        config.put("alias_name", alias);
        deleteAction.put("delete_alias", config);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actions", Collections.singletonList(deleteAction));

        restTemplate.postForEntity(QDRANT_URL + "/collections/aliases",
            new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    @SuppressWarnings("unchecked")
    private void deleteLeftoverVersionedCollections() {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                QDRANT_URL + "/collections", Map.class);
            if (response == null) return;

            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result == null) return;

            List<Map<String, Object>> collections =
                (List<Map<String, Object>>) result.get("collections");
            if (collections == null) return;

            for (Map<String, Object> col : collections) {
                String name = (String) col.get("name");
                if (name != null && name.startsWith("postgrad_rag_") && !name.equals("postgrad_rag_main")) {
                    try {
                        manager.deleteCollection(name);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
