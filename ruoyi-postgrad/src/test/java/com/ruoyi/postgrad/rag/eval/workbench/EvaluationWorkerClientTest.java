package com.ruoyi.postgrad.rag.eval.workbench;

import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationDispatchRequest;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationWorkerClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvaluationWorkerClientTest {

    @Test
    void dispatchRun_serializesFastApiContractWithSnakeCaseAndLists() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        EvaluationWorkerClient client = new EvaluationWorkerClient(
            "http://worker.test", "internal-token", restTemplate);

        EvaluationDispatchRequest request = new EvaluationDispatchRequest(
            "run-1",
            "dataset-sha",
            List.of(new EvaluationDispatchRequest.ConfigVersion(
                3L,
                "v1",
                "business prompt",
                "routing prompt",
                "deepseek-v3",
                new BigDecimal("0.7"),
                "tool-sha"
            )),
            List.of(new EvaluationDispatchRequest.DispatchCase(
                59L,
                "agent-001",
                "北京有哪些985学校？",
                "PROGRAM_FACTS",
                "program_facts",
                "medium",
                new EvaluationDispatchRequest.AgentCaseSpec(
                    List.of("searchPrograms"),
                    List.of("addDraftCandidate"),
                    false,
                    List.of("清华大学"),
                    List.of("保证录取"),
                    List.of(new EvaluationDispatchRequest.ReferenceEvidence(
                        "beijing-985",
                        List.of("清华大学")
                    ))
                )
            ))
        );

        server.expect(once(), requestTo("http://worker.test/runs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Evaluation-Token", "internal-token"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "run_id":"run-1",
                  "dataset_checksum":"dataset-sha",
                  "config_versions":[{
                    "id":3,
                    "version_name":"v1",
                    "business_prompt_text":"business prompt",
                    "routing_prompt_text":"routing prompt",
                    "model":"deepseek-v3",
                    "temperature":0.7,
                    "tool_catalog_checksum":"tool-sha"
                  }],
                  "cases":[{
                    "id":59,
                    "case_key":"agent-001",
                    "query":"北京有哪些985学校？",
                    "expected_route":"PROGRAM_FACTS",
                    "category":"program_facts",
                    "difficulty":"medium",
                    "agent":{
                      "required_tools":["searchPrograms"],
                      "forbidden_tools":["addDraftCandidate"],
                      "expected_abstention":false,
                      "expected_claims":["清华大学"],
                      "forbidden_claims":["保证录取"],
                      "reference_evidence":[{
                        "evidence_key":"beijing-985",
                        "citation_tokens":["清华大学"]
                      }]
                    }
                  }]
                }
                """, true))
            .andRespond(withSuccess("{\"run_id\":\"run-1\",\"status\":\"accepted\"}", MediaType.APPLICATION_JSON));

        client.dispatchRun(request);

        server.verify();
    }
}
