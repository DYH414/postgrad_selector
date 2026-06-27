package com.ruoyi.postgrad.rag.reranker;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class SiliconFlowReranker implements ScoringModel {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowReranker.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxChunksPerDoc;
    private final int overlapTokens;

    public SiliconFlowReranker(RestTemplate restTemplate,
                               String baseUrl,
                               String apiKey,
                               String modelName,
                               int maxChunksPerDoc,
                               int overlapTokens) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxChunksPerDoc = maxChunksPerDoc;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(Collections.emptyList());
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            JSONObject body = new JSONObject();
            body.put("model", modelName);
            body.put("query", query);
            body.put("documents", segments.stream().map(TextSegment::text).toList());
            body.put("max_chunks_per_doc", maxChunksPerDoc);
            body.put("overlap_tokens", overlapTokens);
            body.put("return_documents", false);

            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/rerank",
                new HttpEntity<>(body.toJSONString(), headers),
                String.class);

            JSONObject payload = JSON.parseObject(response.getBody());
            JSONArray results = payload.getJSONArray("results");
            List<Double> scores = new ArrayList<>(Collections.nCopies(segments.size(), 0.0d));
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    Integer index = item.getInteger("index");
                    Double score = item.getDouble("relevance_score");
                    if (index != null && index >= 0 && index < scores.size() && score != null) {
                        scores.set(index, score);
                    }
                }
            }
            return Response.from(scores);
        } catch (RestClientException ex) {
            log.warn("[RagRerank] SiliconFlow rerank failed, fallback to vector order: {}", ex.getMessage());
            throw ex;
        }
    }
}
