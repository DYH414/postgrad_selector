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

    private static final String FROZEN_V3_RESOURCE = "rag-eval/frozen-v3.jsonl";
    private static final String FROZEN_V3_MANIFEST = "rag-eval/frozen-v3.manifest.json";
    private static final String SAFETY_V3_RESOURCE = "rag-eval/safety-v3.jsonl";
    private static final String SAFETY_V3_MANIFEST = "rag-eval/safety-v3.manifest.json";

    private final String resourcePath;
    private final String manifestPath;

    public RagEvaluationDatasetLoader() {
        this(null, null);
    }

    RagEvaluationDatasetLoader(String resourcePath, String manifestPath) {
        this.resourcePath = resourcePath;
        this.manifestPath = manifestPath;
    }

    public List<RagEvaluationCase> loadFrozenV3() {
        return readJsonLines(FROZEN_V3_RESOURCE, 36);
    }

    public List<RagEvaluationCase> loadSafetyV3() {
        return readJsonLines(SAFETY_V3_RESOURCE, 12);
    }

    public String verifyFrozenV3Checksum() {
        return verifyChecksum(FROZEN_V3_RESOURCE, FROZEN_V3_MANIFEST, 36);
    }

    public String verifySafetyV3Checksum() {
        return verifyChecksum(SAFETY_V3_RESOURCE, SAFETY_V3_MANIFEST, 12);
    }

    private List<RagEvaluationCase> readJsonLines(String defaultResource, int expectedCount) {
        String path = resourcePath == null ? defaultResource : resourcePath;
        return readJsonLines(readBytes(path), path, expectedCount);
    }

    List<RagEvaluationCase> readJsonLines(byte[] bytes, String sourceName, int expectedCount) {
        List<RagEvaluationCase> cases = Arrays.stream(new String(bytes, StandardCharsets.UTF_8)
                .split("\\R"))
            .filter(line -> !line.isBlank())
            .map(line -> JSON.parseObject(line, RagEvaluationCase.class))
            .toList();
        if (cases.size() != expectedCount) {
            throw new IllegalStateException(sourceName + " expected " + expectedCount
                + " cases but found " + cases.size());
        }
        return cases;
    }

    private String verifyChecksum(String defaultResource, String defaultManifest, int expectedCount) {
        String dataPath = resourcePath == null ? defaultResource : resourcePath;
        String manifestPathToRead = manifestPath == null ? defaultManifest : manifestPath;
        byte[] bytes = readBytes(dataPath);
        Manifest manifest = JSON.parseObject(readBytes(manifestPathToRead), Manifest.class);
        return verifyChecksum(bytes, manifest, dataPath, expectedCount);
    }

    String verifyChecksum(byte[] bytes, Manifest manifest, String sourceName, int expectedCount) {
        if (manifest.caseCount != expectedCount) {
            throw new IllegalStateException(sourceName + " manifest caseCount mismatch");
        }
        readJsonLines(bytes, sourceName, expectedCount);
        String actual = sha256Hex(canonicalTextBytes(bytes));
        if (!Objects.equals(actual, manifest.sha256)) {
            throw new IllegalStateException(sourceName + " checksum mismatch");
        }
        return actual;
    }

    private byte[] canonicalTextBytes(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
        return text.getBytes(StandardCharsets.UTF_8);
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
