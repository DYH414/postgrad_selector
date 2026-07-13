package com.ruoyi.postgrad.rag.eval;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RagEvaluationDatasetLoader {

    private static final String FROZEN_RESOURCE = "rag-eval/frozen-v1.jsonl";
    private static final String FROZEN_MANIFEST = "rag-eval/frozen-v1.manifest.json";
    private static final String SAFETY_RESOURCE = "rag-eval/safety-v1.jsonl";
    private static final String SAFETY_MANIFEST = "rag-eval/safety-v1.manifest.json";

    private final String resourcePath;
    private final String manifestPath;

    public RagEvaluationDatasetLoader() {
        this(null, null);
    }

    RagEvaluationDatasetLoader(String resourcePath, String manifestPath) {
        this.resourcePath = resourcePath;
        this.manifestPath = manifestPath;
    }

    public List<RagEvaluationCase> loadFrozenV1() {
        return readJsonLines(FROZEN_RESOURCE, 18);
    }

    public List<RagEvaluationCase> loadSafetyV1() {
        return readJsonLines(SAFETY_RESOURCE, 4);
    }

    public String verifyFrozenV1Checksum() {
        return verifyChecksum(FROZEN_RESOURCE, FROZEN_MANIFEST);
    }

    public String verifySafetyV1Checksum() {
        return verifyChecksum(SAFETY_RESOURCE, SAFETY_MANIFEST);
    }

    private List<RagEvaluationCase> readJsonLines(String defaultResource, int expectedCount) {
        String path = resourcePath == null ? defaultResource : resourcePath;
        List<RagEvaluationCase> cases = Arrays.stream(new String(readBytes(path), StandardCharsets.UTF_8)
                .split("\\R"))
            .filter(line -> !line.isBlank())
            .map(line -> JSON.parseObject(line, RagEvaluationCase.class))
            .toList();
        if (cases.size() != expectedCount) {
            throw new IllegalStateException(path + " expected " + expectedCount
                + " cases but found " + cases.size());
        }
        return cases;
    }

    private String verifyChecksum(String defaultResource, String defaultManifest) {
        String dataPath = resourcePath == null ? defaultResource : resourcePath;
        String manifestPathToRead = manifestPath == null ? defaultManifest : manifestPath;
        byte[] bytes = readBytes(dataPath);
        Manifest manifest = JSON.parseObject(readBytes(manifestPathToRead), Manifest.class);
        String actual = sha256Hex(bytes);
        if (!Objects.equals(actual, manifest.sha256)) {
            throw new IllegalStateException(dataPath + " checksum mismatch");
        }
        return actual;
    }

    private byte[] readBytes(String path) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read classpath resource: " + path, e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static class Manifest {
        public String version;
        public int caseCount;
        public String sha256;

        public Manifest() {}
    }
}
