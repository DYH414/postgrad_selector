package com.ruoyi.postgrad.rag.eval.workbench;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class RagEvaluationWorkbenchMapperXmlTest {
    @Test
    void mapperContainsEveryPersistenceContract() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mapper/postgrad/RagEvaluationWorkbenchMapper.xml")) {
            assertNotNull(in);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(in);
            for (String id : new String[] { "insertDataset", "selectDatasetById", "insertCase", "selectCaseById",
                "insertRun", "selectRunById", "insertCaseResult", "selectCaseResultsByRunId" }) {
                String count = XPathFactory.newInstance().newXPath()
                    .evaluate("count(//*[@id='" + id + "'])", document);
                org.junit.jupiter.api.Assertions.assertEquals("1", count, id);
            }
        }
    }

    @Test
    void selectRunsUsesBoundKeywordAndChecksumFilters() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mapper/postgrad/RagEvaluationWorkbenchMapper.xml")) {
            assertNotNull(in);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("runIdKeyword != null and runIdKeyword != ''"));
            assertTrue(xml.contains("run_id LIKE CONCAT('%', #{runIdKeyword}, '%')"));
            assertTrue(xml.contains("datasetChecksum != null and datasetChecksum != ''"));
            assertTrue(xml.contains("dataset_checksum = #{datasetChecksum}"));
            assertFalse(xml.contains("${runIdKeyword}"));
            assertFalse(xml.contains("${datasetChecksum}"));
        }
    }

    @Test
    void runProgressLocksRunRowAndNeverOverwritesInterruptedState() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mapper/postgrad/RagEvaluationWorkbenchMapper.xml")) {
            assertNotNull(in);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");

            assertTrue(xml.contains("id=\"selectRunByIdForUpdate\""));
            assertTrue(xml.contains("SELECT * FROM rag_eval_run WHERE run_id=#{runId} FOR UPDATE"));
            assertTrue(xml.contains("status NOT IN ('SUCCEEDED','FAILED','CANCELLED','INTERRUPTED')"));
        }
    }
}
