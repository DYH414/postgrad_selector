package com.ruoyi.postgrad.rag.qdrant;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

public class QdrantCollectionService {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionService.class);

    private final RestTemplate restTemplate;
    private final String url;
    private final String apiKey;
    private final String collectionName;
    private final int dimension;

    public QdrantCollectionService(RestTemplate restTemplate,
                                   String url,
                                   String apiKey,
                                   String collectionName,
                                   int dimension) {
        this.restTemplate = restTemplate;
        this.url = trimTrailingSlash(url);
        this.apiKey = apiKey;
        this.collectionName = collectionName;
        this.dimension = dimension;
    }

    public void ensureCollection() {
        try {
            restTemplate.exchange(collectionUrl(), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            return;
        } catch (HttpClientErrorException.NotFound ex) {
            // Create the collection only when Qdrant explicitly reports it missing.
        } catch (RestClientResponseException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw ex;
        }

        createCollection();
    }

    /**
     * Deletes and recreates the configured collection.
     *
     * @deprecated Use {@link RagCollectionManager} for versioned collection
     *             lifecycle management instead. This method is retained only
     *             for the initial bootstrapping path and will be removed in a
     *             future release.
     */
    @Deprecated
    public void clearCollection() {
        try {
            restTemplate.exchange(collectionUrl(), HttpMethod.DELETE, new HttpEntity<>(headers()), String.class);
        } catch (HttpClientErrorException.NotFound ignored) {
            // Missing collection is already empty; recreate below.
        }
        createCollection();
    }

    public boolean isReachable() {
        try {
            restTemplate.exchange(collectionUrl(), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            return true;
        } catch (RestClientException ex) {
            log.debug("[RagQdrant] Collection {} is not reachable: {}", collectionName, ex.getMessage());
            return false;
        }
    }

    public String getCollectionName() {
        return collectionName;
    }

    private String collectionUrl() {
        return url + "/collections/" + collectionName;
    }

    private void createCollection() {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);

        restTemplate.put(collectionUrl(), new HttpEntity<>(body, headers()));
        log.info("[RagQdrant] Collection {} created or updated, dimension={}", collectionName, dimension);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("api-key", apiKey);
        }
        return headers;
    }

    private static String trimTrailingSlash(String source) {
        if (source == null || source.isBlank()) {
            return "http://localhost:6333";
        }
        return source.endsWith("/") ? source.substring(0, source.length() - 1) : source;
    }
}
