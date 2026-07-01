package com.yy.agent.contractmvp.ai.agent;

import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractQaRequest;
import com.yy.agent.contractmvp.api.dto.ContractQaResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
import com.yy.agent.contractmvp.api.dto.PolicyQaResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentOrchestratorTest {

    @Test
    void shouldRunAgentAndAppendTrace() {
        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator();
        AgentContext context = new AgentContext("demo-workflow", "CTR-1");

        String output = orchestrator.run(
                context,
                (ctx, input) -> AgentResult.of(input + "-done", "DemoAgent", "processed " + ctx.contractId()),
                "work"
        );

        assertThat(output).isEqualTo("work-done");
        assertThat(context.traces())
                .extracting(AgentTrace::agentName)
                .containsExactly("DemoAgent");
        assertThat(context.traces().getFirst().summary()).isEqualTo("processed CTR-1");
        assertThat(context.traces().getFirst().retrievedChunkIds()).isEmpty();
        assertThat(context.traces().getFirst().retrievedPolicyIds()).isEmpty();
    }

    @Test
    void responseDtosShouldKeepBackwardCompatibleConstructorsAndTraceLists() {
        ContractRiskCheckResponse risk = new ContractRiskCheckResponse("ok", List.of());
        ContractQaRequest qaRequest = new ContractQaRequest("question");
        ContractQaResponse qa = new ContractQaResponse("answer", List.of("c1"), List.of("p1"));
        PolicyQaResponse policyQa = new PolicyQaResponse("answer", List.of("p1"));
        ApprovalAssistResponse approval = new ApprovalAssistResponse("go", List.of("check"), List.of("c1"), List.of("p1"));

        assertThat(qaRequest.includePolicyEvidence()).isFalse();
        assertThat(risk.agentTrace()).isEmpty();
        assertThat(qa.agentTrace()).isEmpty();
        assertThat(policyQa.agentTrace()).isEmpty();
        assertThat(approval.agentTrace()).isEmpty();

        AgentTrace trace = new AgentTrace("FinalAgent", "merged outputs");
        ContractRiskCheckResponse tracedRisk = new ContractRiskCheckResponse("ok", List.of(), List.of(trace));
        ContractQaResponse tracedQa = new ContractQaResponse("answer", List.of("c1"), List.of("p1"), List.of(trace));
        PolicyQaResponse tracedPolicyQa = new PolicyQaResponse("answer", List.of("p1"), List.of(trace));
        ApprovalAssistResponse tracedApproval = new ApprovalAssistResponse(
                "go",
                List.of("check"),
                List.of("c1"),
                List.of("p1"),
                List.of(trace)
        );

        assertThat(tracedRisk.agentTrace()).containsExactly(trace);
        assertThat(tracedQa.agentTrace()).containsExactly(trace);
        assertThat(tracedPolicyQa.agentTrace()).containsExactly(trace);
        assertThat(tracedApproval.agentTrace()).containsExactly(trace);

        AgentTrace evidenceTrace = new AgentTrace(
                "EvidenceAgent",
                "loaded evidence",
                List.of("c1", "c1", " "),
                List.of("p1", "p1")
        );
        assertThat(evidenceTrace.retrievedChunkIds()).containsExactly("c1");
        assertThat(evidenceTrace.retrievedPolicyIds()).containsExactly("p1");
    }
}
