package com.ruoyi.postgrad.rag.qdrant;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * Manages Qdrant collection lifecycle: versioned collection creation, payload
 * indexes, alias switching, resolution, point counting, and deletion.
 *
 * <p>All operations communicate with the Qdrant REST API over HTTP.
 */
public class RagCollectionManager {

    private static final Logger log = LoggerFactory.getLogger(RagCollectionManager.class);

    private static final String COLLECTION_PREFIX = "postgrad_rag_";

    private static final String[] INDEXED_FIELDS = {
        "source_type", "school_id", "school_name", "program_id", "year", "doc_type", "section"
    };

    private final RestTemplate restTemplate;
    private final String qdrantUrl;
    private final String apiKey;

    public RagCollectionManager(RestTemplate restTemplate, String qdrantUrl) {
        this(restTemplate, qdrantUrl, "");
    }

    public RagCollectionManager(RestTemplate restTemplate, String qdrantUrl, String apiKey) {
        this.restTemplate = restTemplate;
        this.qdrantUrl = qdrantUrl.endsWith("/")
            ? qdrantUrl.substring(0, qdrantUrl.length() - 1)
            : qdrantUrl;
        this.apiKey = apiKey;
    }

    /**
     * Creates a versioned Qdrant collection with a unique name derived from
     * the current timestamp and a short random identifier.
     *
     * @param dimension embedding vector dimension
     * @return the generated collection name (never null)
     */
    public String createVersionCollection(int dimension) {
        String collectionName = COLLECTION_PREFIX
            + System.currentTimeMillis() + "_"
            + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);

        restTemplate.put(qdrantUrl + "/collections/" + collectionName,
            new HttpEntity<>(body, jsonHeaders()));

        log.info("[RagCollectionManager] Created collection {} with dimension={}",
            collectionName, dimension);
        return collectionName;
    }

    /**
     * Creates keyword payload indexes on the given collection for all
     * metadata fields used in RAG filtering.
     *
     * @param collectionName target collection
     */
    public void createPayloadIndexes(String collectionName) {
        for (String field : INDEXED_FIELDS) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("field_name", field);
            body.put("field_schema", "keyword");

            restTemplate.put(qdrantUrl + "/collections/" + collectionName + "/index",
                new HttpEntity<>(body, jsonHeaders()));
        }
        log.info("[RagCollectionManager] Created {} payload indexes on {}",
            INDEXED_FIELDS.length, collectionName);
    }

    /**
     * Atomically creates or switches an alias to point at the given
     * collection. Qdrant's {@code create_alias} operation is idempotent
     * and replaces any existing alias with the same name.
     *
     * @param aliasName        the alias name (e.g. "postgrad_rag_active")
     * @param targetCollection collection the alias should point to
     */
    public void switchAlias(String aliasName, String targetCollection) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (resolveAlias(aliasName) != null) {
            Map<String, Object> deleteAction = new LinkedHashMap<>();
            Map<String, String> deleteConfig = new LinkedHashMap<>();
            deleteConfig.put("alias_name", aliasName);
            deleteAction.put("delete_alias", deleteConfig);
            actions.add(deleteAction);
        }

        Map<String, Object> createAction = new LinkedHashMap<>();
        Map<String, String> aliasConfig = new LinkedHashMap<>();
        aliasConfig.put("alias_name", aliasName);
        aliasConfig.put("collection_name", targetCollection);
        createAction.put("create_alias", aliasConfig);
        actions.add(createAction);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actions", actions);

        restTemplate.postForEntity(qdrantUrl + "/collections/aliases",
            new HttpEntity<>(body, jsonHeaders()), String.class);

        log.info("[RagCollectionManager] Alias '{}' -> '{}'", aliasName, targetCollection);
    }

    /**
     * Resolves an alias name to the collection it points to.
     *
     * @param aliasName the alias to resolve
     * @return collection name, or {@code null} if no such alias exists
     */
    public String resolveAlias(String aliasName) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                qdrantUrl + "/aliases", Map.class);
            if (response == null) return null;

            Map<String, Object> result = extractResult(response);
            if (result == null) return null;

            List<Map<String, Object>> aliases = castList(result.get("aliases"));
            if (aliases == null) return null;

            for (Map<String, Object> alias : aliases) {
                if (aliasName.equals(alias.get("alias_name"))) {
                    return (String) alias.get("collection_name");
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[RagCollectionManager] Failed to resolve alias '{}': {}",
                aliasName, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the number of points in a collection, or -1 if the
     * collection does not exist or cannot be reached.
     *
     * @param collectionName target collection
     * @return point count, or -1 on failure
     */
    public long getPointCount(String collectionName) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                qdrantUrl + "/collections/" + collectionName, Map.class);
            if (response == null) return -1;

            Map<String, Object> result = extractResult(response);
            if (result == null) return -1;

            Object count = result.get("points_count");
            if (count instanceof Number) {
                return ((Number) count).longValue();
            }
            return 0;
        } catch (Exception e) {
            log.warn("[RagCollectionManager] Failed to get point count for '{}': {}",
                collectionName, e.getMessage());
            return -1;
        }
    }

    /**
     * Deletes a Qdrant collection by name.
     *
     * @param collectionName the collection to delete
     */
    public void deleteCollection(String collectionName) {
        restTemplate.exchange(qdrantUrl + "/collections/" + collectionName,
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
        log.info("[RagCollectionManager] Deleted collection '{}'", collectionName);
    }

    // ---- internal helpers ----

    private Map<String, Object> extractResult(Map<String, Object> response) {
        if (response == null) return null;
        Object raw = response.get("result");
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) raw;
            return result;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }
        return null;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("api-key", apiKey);
        }
        return headers;
    }
}
