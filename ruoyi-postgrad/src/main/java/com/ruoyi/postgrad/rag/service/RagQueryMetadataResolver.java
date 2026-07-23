package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.domain.School;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves entity mentions in natural-language RAG queries into metadata filters.
 */
@Service
public class RagQueryMetadataResolver {

    private static final Logger log = LoggerFactory.getLogger(RagQueryMetadataResolver.class);

    private final SchoolMapper schoolMapper;

    public RagQueryMetadataResolver(SchoolMapper schoolMapper) {
        this.schoolMapper = schoolMapper;
    }

    public void enrich(RagSearchTestRequest request) {
        if (request == null) {
            return;
        }
        if (notBlank(request.getSchoolName())) {
            request.setSchoolId(null);
            applyCorpusScope(request);
            return;
        }
        try {
            if (notBlank(request.getSchoolId())) {
                resolveSchoolId(request);
            } else if (notBlank(request.getQuery())) {
                resolveSchoolMention(request);
            }
        } catch (Exception e) {
            log.warn("[RagQueryMetadataResolver] Failed to resolve query metadata: {}", e.getMessage());
        }
        applyCorpusScope(request);
    }

    private void applyCorpusScope(RagSearchTestRequest request) {
        boolean schoolScoped = notBlank(request.getSchoolName())
            || "admission_guide".equals(request.getDocType());
        if (!notBlank(request.getSourceType())) {
            request.setSourceType(schoolScoped ? "school_guide" : "general");
        }
        if (schoolScoped && !notBlank(request.getDocType())) {
            request.setDocType("admission_guide");
        }
    }

    private void resolveSchoolMention(RagSearchTestRequest request) {
        List<School> schools = schoolMapper.selectSchoolAll();
        if (schools == null || schools.isEmpty()) {
            return;
        }
        String query = request.getQuery().trim();
        schools.stream()
            .filter(school -> school != null && school.getId() != null && matches(query, school))
            .max(Comparator.comparingInt(this::matchLength))
            .ifPresent(school -> request.setSchoolName(school.getName()));
    }

    private void resolveSchoolId(RagSearchTestRequest request) {
        List<School> schools = schoolMapper.selectSchoolAll();
        if (schools == null || schools.isEmpty()) {
            return;
        }
        schools.stream()
            .filter(school -> school != null && school.getId() != null)
            .filter(school -> String.valueOf(school.getId()).equals(request.getSchoolId()))
            .findFirst()
            .ifPresent(school -> {
                request.setSchoolName(school.getName());
                request.setSchoolId(null);
            });
    }

    private boolean matches(String query, School school) {
        return contains(query, school.getName()) || contains(query, school.getShortName());
    }

    private int matchLength(School school) {
        return Math.max(length(school.getName()), length(school.getShortName()));
    }

    private static boolean contains(String query, String candidate) {
        return candidate != null && candidate.trim().length() >= 2 && query.contains(candidate.trim());
    }

    private static int length(String value) {
        return value != null ? value.trim().length() : 0;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
