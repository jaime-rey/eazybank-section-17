package com.eazybytes.camunda.decision;

import org.camunda.bpm.dmn.engine.DmnDecisionRuleResult;
import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.engine.DecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CreditApprovalDecisionIT {

    private static final String DECISION_KEY = "credit-approval";

    @Autowired
    private DecisionService decisionService;

    @Test
    void withHighScoreAndLowLimit_returnsAutoApproved() {
        DmnDecisionTableResult result = evaluate(800, 5000);

        assertThat((List<DmnDecisionRuleResult>) result).hasSize(1);
        assertThat(result.getFirstResult().<Boolean>getEntry("approved")).isEqualTo(true);
        assertThat(result.getFirstResult().<String>getEntry("reason")).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void withHighScoreAndHighLimit_returnsLimitTooHigh() {
        DmnDecisionTableResult result = evaluate(720, 25000);

        assertThat((List<DmnDecisionRuleResult>) result).hasSize(1);
        assertThat(result.getFirstResult().<Boolean>getEntry("approved")).isEqualTo(false);
        assertThat(result.getFirstResult().<String>getEntry("reason")).isEqualTo("LIMIT_TOO_HIGH_FOR_SCORE");
    }

    @Test
    void withMidScore_returnsManualReview() {
        DmnDecisionTableResult result = evaluate(600, 5000);

        assertThat((List<DmnDecisionRuleResult>) result).hasSize(1);
        assertThat(result.getFirstResult().<Boolean>getEntry("approved")).isEqualTo(false);
        assertThat(result.getFirstResult().<String>getEntry("reason")).isEqualTo("MANUAL_REVIEW_REQUIRED");
    }

    @Test
    void withLowScore_returnsRejectedLowScore() {
        DmnDecisionTableResult result = evaluate(400, 5000);

        assertThat((List<DmnDecisionRuleResult>) result).hasSize(1);
        assertThat(result.getFirstResult().<Boolean>getEntry("approved")).isEqualTo(false);
        assertThat(result.getFirstResult().<String>getEntry("reason")).isEqualTo("REJECTED_LOW_SCORE");
    }

    @Test
    void withUncoveredInputs_returnsEmpty() {
        DmnDecisionTableResult result = evaluate(720, 5000);

        assertThat((List<DmnDecisionRuleResult>) result).isEmpty();
    }

    private DmnDecisionTableResult evaluate(int creditScore, int requestedLimit) {
        Map<String, Object> variables = Map.of(
                "creditScore", creditScore,
                "requestedLimit", requestedLimit
        );
        return decisionService
                .evaluateDecisionTableByKey(DECISION_KEY)
                .variables(variables)
                .evaluate();
    }
}
