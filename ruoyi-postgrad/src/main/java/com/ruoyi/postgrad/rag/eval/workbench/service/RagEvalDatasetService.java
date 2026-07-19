package com.ruoyi.postgrad.rag.eval.workbench.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.RagEvaluationCase;
import com.ruoyi.postgrad.rag.eval.RagEvaluationDatasetLoader;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagEvalDatasetService {
    private static final Path DEFAULT_EXPORT_DIR = Path.of("target", "rag-eval-datasets");
    private final RagEvaluationWorkbenchMapper mapper;
    private final Path exportDir;
    private final RagEvaluationDatasetLoader resourceLoader = new RagEvaluationDatasetLoader();

    @Autowired
    public RagEvalDatasetService(RagEvaluationWorkbenchMapper mapper) {
        this(mapper, DEFAULT_EXPORT_DIR);
    }

    public RagEvalDatasetService(RagEvaluationWorkbenchMapper mapper, Path exportDir) {
        this.mapper = mapper;
        this.exportDir = exportDir;
    }

    public List<RagEvalDataset> list(String type, String state) {
        return mapper.selectDatasets(type, state);
    }

    public RagEvalDataset get(Long id) {
        RagEvalDataset dataset = mapper.selectDatasetById(id);
        if (dataset == null) throw new ServiceException("Evaluation dataset not found");
        return dataset;
    }

    public List<RagEvalCaseEntity> listCases(Long datasetId) {
        get(datasetId);
        return mapper.selectCasesByDatasetId(datasetId);
    }

    public RagEvalDataset create(RagEvalDataset dataset, String operator) {
        if (blank(dataset.getDatasetKey()) || blank(dataset.getName()) || blank(dataset.getVersion())) {
            throw new ServiceException("datasetKey, name and version are required");
        }
        String type = normalize(dataset.getDatasetType(), "DEV");
        if (!List.of("DEV", "SAFETY").contains(type)) {
            throw new ServiceException("New datasets must be DEV or SAFETY");
        }
        dataset.setDatasetType(type);
        dataset.setLifecycleState("DRAFT");
        dataset.setCaseCount(0);
        dataset.setLockVersion(0);
        dataset.setCreatedBy(operator);
        dataset.setUpdatedBy(operator);
        mapper.insertDataset(dataset);
        return dataset;
    }

    public RagEvalDataset update(RagEvalDataset dataset, String operator) {
        RagEvalDataset current = get(dataset.getId());
        ensureEditable(current);
        String nextState = normalize(dataset.getLifecycleState(), current.getLifecycleState());
        if (!List.of("DRAFT", "READY").contains(nextState)) {
            throw new ServiceException("Editable datasets may only transition between DRAFT and READY");
        }
        dataset.setLifecycleState(nextState);
        dataset.setUpdatedBy(operator);
        if (mapper.updateDataset(dataset) != 1) {
            throw new ServiceException("Dataset changed concurrently; refresh and retry");
        }
        return get(dataset.getId());
    }

    @Transactional
    public RagEvalCaseEntity saveCase(RagEvalCaseEntity evalCase, String operator) {
        RagEvalDataset dataset = get(evalCase.getDatasetId());
        ensureEditable(dataset);
        validateCase(evalCase);
        evalCase.setDifficulty(normalize(evalCase.getDifficulty(), "medium").toLowerCase());
        evalCase.setCreatedBy(operator);
        evalCase.setUpdatedBy(operator);
        if (evalCase.getId() == null) mapper.insertCase(evalCase);
        else if (mapper.updateCase(evalCase) != 1) throw new ServiceException("Case is immutable or no longer exists");
        refreshCount(dataset.getId());
        return mapper.selectCaseById(evalCase.getId());
    }

    @Transactional
    public void deleteCase(Long id) {
        RagEvalCaseEntity evalCase = mapper.selectCaseById(id);
        if (evalCase == null) throw new ServiceException("Evaluation case not found");
        ensureEditable(get(evalCase.getDatasetId()));
        if (mapper.deleteCase(id) != 1) throw new ServiceException("Case could not be deleted");
        refreshCount(evalCase.getDatasetId());
    }

    @Transactional
    public RagEvalDataset freeze(Long sourceDatasetId, String frozenVersion, String operator) {
        RagEvalDataset source = get(sourceDatasetId);
        ensureEditable(source);
        if (blank(frozenVersion)) throw new ServiceException("Frozen version is required");
        List<RagEvalCaseEntity> cases = mapper.selectCasesByDatasetId(sourceDatasetId);
        if (cases.isEmpty()) throw new ServiceException("Cannot freeze an empty dataset");
        cases.forEach(this::validateCase);
        String jsonl = canonicalJsonl(cases);
        String checksum = sha256(jsonl.getBytes(StandardCharsets.UTF_8));

        RagEvalDataset frozen = new RagEvalDataset();
        frozen.setDatasetKey(source.getDatasetKey());
        frozen.setName(source.getName());
        frozen.setVersion(frozenVersion);
        frozen.setDatasetType(source.getDatasetType().equals("SAFETY") ? "SAFETY" : "FROZEN");
        frozen.setLifecycleState("FROZEN");
        frozen.setDescription(source.getDescription());
        frozen.setSourceDatasetId(sourceDatasetId);
        frozen.setChecksumSha256(checksum);
        frozen.setCaseCount(cases.size());
        frozen.setLockVersion(0);
        frozen.setCreatedBy(operator);
        frozen.setUpdatedBy(operator);
        mapper.insertDataset(frozen);
        for (RagEvalCaseEntity sourceCase : cases) {
            RagEvalCaseEntity copy = copyCase(sourceCase, frozen.getId(), operator);
            mapper.insertCase(copy);
        }
        writeSnapshot(frozen, jsonl, checksum);
        return frozen;
    }

    @Transactional
    public RagEvalDataset importBundled(String resourceName, String operator) {
        boolean safety = "safety-v1".equals(resourceName);
        RagEvalDataset existing = mapper.selectDatasetByKeyVersion(resourceName, "v1");
        if (existing != null) return existing;
        List<RagEvaluationCase> source = safety ? resourceLoader.loadSafetyV1() : resourceLoader.loadFrozenV1();
        String checksum = safety ? resourceLoader.verifySafetyV1Checksum() : resourceLoader.verifyFrozenV1Checksum();
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setDatasetKey(resourceName);
        dataset.setName(safety ? "Safety v1" : "Frozen v1");
        dataset.setVersion("v1");
        dataset.setDatasetType(safety ? "SAFETY" : "FROZEN");
        dataset.setLifecycleState("FROZEN");
        dataset.setChecksumSha256(checksum);
        dataset.setCaseCount(source.size());
        dataset.setLockVersion(0);
        dataset.setCreatedBy(operator);
        dataset.setUpdatedBy(operator);
        mapper.insertDataset(dataset);
        for (RagEvaluationCase item : source) mapper.insertCase(fromEvaluationCase(item, dataset.getId(), operator));
        return dataset;
    }

    @Transactional
    public RagEvalDataset importJsonl(String datasetKey, String name, String version,
                                      String datasetType, String jsonl, String operator) {
        if (blank(jsonl)) throw new ServiceException("JSONL file is empty");
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setDatasetKey(datasetKey); dataset.setName(name); dataset.setVersion(version);
        dataset.setDatasetType(datasetType);
        create(dataset, operator);
        int count = 0;
        for (String line : jsonl.split("\\R")) {
            if (line.isBlank()) continue;
            RagEvaluationCase source;
            try { source = JSON.parseObject(line, RagEvaluationCase.class); }
            catch (RuntimeException ex) { throw new ServiceException("Invalid JSONL at line " + (count + 1)); }
            RagEvalCaseEntity evalCase = fromEvaluationCase(source, dataset.getId(), operator);
            validateCase(evalCase);
            mapper.insertCase(evalCase);
            count++;
        }
        if (count == 0) throw new ServiceException("JSONL contains no evaluation cases");
        mapper.updateDatasetCount(dataset.getId(), count);
        dataset.setCaseCount(count);
        return dataset;
    }

    public List<RagEvaluationCase> toEvaluationCases(Long datasetId) {
        return mapper.selectCasesByDatasetId(datasetId).stream().map(this::toEvaluationCase).toList();
    }

    public String exportJsonl(Long datasetId) {
        return canonicalJsonl(mapper.selectCasesByDatasetId(datasetId));
    }

    private void validateCase(RagEvalCaseEntity c) {
        if (blank(c.getCaseKey()) || blank(c.getQueryText())) throw new ServiceException("caseKey and query are required");
        boolean noAnswer = Boolean.TRUE.equals(c.getNoAnswer());
        boolean hasConditions = !emptyJsonArray(c.getRelevantConditionsJson());
        boolean hasSources = !emptyJsonArray(c.getRelevantSourcesJson());
        if (!noAnswer && !hasConditions && !hasSources) {
            throw new ServiceException("Positive cases require relevant conditions or relevant sources");
        }
    }

    private String canonicalJsonl(List<RagEvalCaseEntity> entities) {
        return entities.stream().sorted(Comparator.comparing(RagEvalCaseEntity::getCaseKey))
            .map(this::toEvaluationCase)
            .map(value -> JSON.toJSONString(value, JSONWriter.Feature.MapSortField))
            .reduce((a, b) -> a + "\n" + b).orElse("") + "\n";
    }

    private void writeSnapshot(RagEvalDataset dataset, String jsonl, String checksum) {
        try {
            Files.createDirectories(exportDir);
            String base = dataset.getDatasetKey() + "-" + dataset.getVersion();
            Files.writeString(exportDir.resolve(base + ".jsonl"), jsonl, StandardCharsets.UTF_8);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("version", dataset.getVersion());
            manifest.put("caseCount", dataset.getCaseCount());
            manifest.put("sha256", checksum);
            Files.writeString(exportDir.resolve(base + ".manifest.json"), JSON.toJSONString(manifest), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("Unable to write frozen dataset snapshot").setDetailMessage(e.getMessage());
        }
    }

    private RagEvaluationCase toEvaluationCase(RagEvalCaseEntity e) {
        RagEvaluationCase c = new RagEvaluationCase();
        c.setId(e.getCaseKey()); c.setQuery(e.getQueryText()); c.setCategory(e.getCategory());
        c.setDifficulty(e.getDifficulty()); c.setNoAnswer(Boolean.TRUE.equals(e.getNoAnswer()));
        c.setSourceDataAvailable(!Boolean.FALSE.equals(e.getSourceDataAvailable()));
        c.setExpectedFallback(Boolean.TRUE.equals(e.getExpectedFallback())); c.setExpectedRoute(e.getExpectedRoute());
        c.setExpectedMetadata(parse(e.getExpectedMetadataJson(), new TypeReference<Map<String, String>>() {}, new LinkedHashMap<>()));
        c.setRelevantConditions(parse(e.getRelevantConditionsJson(), new TypeReference<List<RagEvaluationCase.RelevantCondition>>() {}, new ArrayList<>()));
        c.setRelevantSources(parse(e.getRelevantSourcesJson(), new TypeReference<List<String>>() {}, new ArrayList<>()));
        c.setTags(parse(e.getTagsJson(), new TypeReference<List<String>>() {}, new ArrayList<>()));
        return c;
    }

    private RagEvalCaseEntity fromEvaluationCase(RagEvaluationCase c, Long datasetId, String operator) {
        RagEvalCaseEntity e = new RagEvalCaseEntity();
        e.setDatasetId(datasetId); e.setCaseKey(c.getId()); e.setQueryText(c.getQuery()); e.setCategory(c.getCategory());
        e.setDifficulty(c.getDifficulty()); e.setExpectedMetadataJson(JSON.toJSONString(c.getExpectedMetadata()));
        e.setRelevantConditionsJson(JSON.toJSONString(c.getRelevantConditions())); e.setRelevantSourcesJson(JSON.toJSONString(c.getRelevantSources()));
        e.setNoAnswer(c.isNoAnswer()); e.setSourceDataAvailable(c.isSourceDataAvailable()); e.setExpectedFallback(c.isExpectedFallback());
        e.setExpectedRoute(c.getExpectedRoute()); e.setTagsJson(JSON.toJSONString(c.getTags())); e.setCreatedBy(operator); e.setUpdatedBy(operator);
        return e;
    }

    private RagEvalCaseEntity copyCase(RagEvalCaseEntity s, Long datasetId, String operator) {
        RagEvalCaseEntity c = new RagEvalCaseEntity();
        c.setDatasetId(datasetId); c.setCaseKey(s.getCaseKey()); c.setQueryText(s.getQueryText()); c.setCategory(s.getCategory());
        c.setDifficulty(s.getDifficulty()); c.setExpectedMetadataJson(s.getExpectedMetadataJson()); c.setRelevantConditionsJson(s.getRelevantConditionsJson());
        c.setRelevantSourcesJson(s.getRelevantSourcesJson()); c.setNoAnswer(s.getNoAnswer()); c.setSourceDataAvailable(s.getSourceDataAvailable());
        c.setExpectedFallback(s.getExpectedFallback()); c.setExpectedRoute(s.getExpectedRoute()); c.setTagsJson(s.getTagsJson());
        c.setReviewerNote(s.getReviewerNote()); c.setCreatedBy(operator); c.setUpdatedBy(operator); return c;
    }

    private void ensureEditable(RagEvalDataset d) {
        if ("FROZEN".equals(d.getLifecycleState())) throw new ServiceException("Frozen datasets are immutable");
    }
    private void refreshCount(Long datasetId) { mapper.updateDatasetCount(datasetId, mapper.selectCasesByDatasetId(datasetId).size()); }
    private boolean emptyJsonArray(String json) { return blank(json) || JSON.parseArray(json).isEmpty(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String normalize(String value, String fallback) { return blank(value) ? fallback : value.trim().toUpperCase(); }
    private <T> T parse(String json, TypeReference<T> type, T fallback) { return blank(json) ? fallback : JSON.parseObject(json, type); }
    private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new ServiceException("SHA-256 unavailable"); }
    }
}
