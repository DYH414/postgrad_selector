package com.ruoyi.postgrad.rag.eval.answer;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RagAnswerEvaluationDatasetLoader {

    private static final String RESOURCE = "rag-eval/answer-v1.jsonl";
    private static final String MANIFEST = "rag-eval/answer-v1.manifest.json";
    private static final int CASE_COUNT = 12;

    public List<RagAnswerEvaluationCase> loadAnswerV1() {
        byte[] bytes = readBytes(RESOURCE);
        List<RagAnswerEvaluationCase> cases = Arrays.stream(
                new String(bytes, StandardCharsets.UTF_8).split("\\R"))
            .filter(line -> !line.isBlank())
            .map(line -> JSON.parseObject(line, RagAnswerEvaluationCase.class))
            .toList();
        if (cases.size() != CASE_COUNT) {
            throw new IllegalStateException(RESOURCE + " expected " + CASE_COUNT
                + " cases but found " + cases.size());
        }
        return cases;
    }

    public String verifyAnswerV1Checksum() {
        byte[] bytes = readBytes(RESOURCE);
        Manifest manifest = JSON.parseObject(readBytes(MANIFEST), Manifest.class);
        if (manifest.caseCount != CASE_COUNT
            || !Objects.equals(manifest.sha256, sha256(canonicalTextBytes(bytes)))) {
            throw new IllegalStateException(RESOURCE + " manifest mismatch");
        }
        loadAnswerV1();
        return manifest.sha256;
    }

    private byte[] canonicalTextBytes(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] readBytes(String path) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing classpath resource: " + path);
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class Manifest {
        public String version;
        public int caseCount;
        public String sha256;
    }
}
