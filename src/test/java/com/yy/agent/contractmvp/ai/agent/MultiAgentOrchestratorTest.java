package com.yy.agent.contractmvp.ai.agent;

import com.yy.agent.contractmvp.api.dto.ApprovalAssistResponse;
import com.yy.agent.contractmvp.api.dto.ContractRiskCheckResponse;
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
    }

    @Test
    void responseDtosShouldKeepBackwardCompatibleConstructorsAndTraceLists() {
        ContractRiskCheckResponse risk = new ContractRiskCheckResponse("ok", List.of());
        ApprovalAssistResponse approval = new ApprovalAssistResponse("go", List.of("check"), List.of("c1"), List.of("p1"));

        assertThat(risk.agentTrace()).isEmpty();
        assertThat(approval.agentTrace()).isEmpty();

        AgentTrace trace = new AgentTrace("FinalAgent", "merged outputs");
        ContractRiskCheckResponse tracedRisk = new ContractRiskCheckResponse("ok", List.of(), List.of(trace));
        ApprovalAssistResponse tracedApproval = new ApprovalAssistResponse(
                "go",
                List.of("check"),
                List.of("c1"),
                List.of("p1"),
                List.of(trace)
        );

        assertThat(tracedRisk.agentTrace()).containsExactly(trace);
        assertThat(tracedApproval.agentTrace()).containsExactly(trace);
    }
}
