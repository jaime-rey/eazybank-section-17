package com.eazybytes.camunda.process;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.bpmn.AbstractAssertions.init;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;

@SpringBootTest
class CreditRequestProcessIT {

    private static final String PROCESS_KEY = "credit-request";

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        init(processEngine);
    }

    @AfterEach
    void tearDown() {
        Mocks.reset();
    }

    @Test
    void autoApprovalPath_endsAtApproved() {
        registerVerifyHistoryWithScore(800);

        ProcessInstance pi = start("APP-AUTO", 5000);

        assertThat(pi)
                .hasPassed(
                        "Task_VerifyHistory",
                        "Task_EvaluateRules",
                        "Task_AutoApprove",
                        "EndEvent_Approved")
                .hasNotPassed("Task_ManualReview", "EndEvent_Rejected")
                .isEnded()
                .variables()
                .containsEntry("dmnReason", "AUTO_APPROVED")
                .containsEntry("approved", true);
    }

    @Test
    void manualPath_thenApproved_endsAtApproved() {
        registerVerifyHistoryWithScore(600);

        ProcessInstance pi = start("APP-MANUAL-YES", 5000);

        assertThat(pi)
                .isNotEnded()
                .isWaitingAt("Task_ManualReview")
                .task()
                .hasName("Manual review")
                .isAssignedTo("admin");

        complete(task(), Map.of("approved", true));

        assertThat(pi)
                .hasPassed("Task_ManualReview", "EndEvent_Approved")
                .hasNotPassed("Task_AutoApprove", "EndEvent_Rejected")
                .isEnded();
    }

    @Test
    void manualPath_thenRejected_endsAtRejected() {
        registerVerifyHistoryWithScore(600);

        ProcessInstance pi = start("APP-MANUAL-NO", 5000);

        assertThat(pi).isWaitingAt("Task_ManualReview");

        complete(task(), Map.of("approved", false));

        assertThat(pi)
                .hasPassed("Task_ManualReview", "EndEvent_Rejected")
                .hasNotPassed("Task_AutoApprove", "EndEvent_Approved")
                .isEnded();
    }

    @Test
    void defaultPath_lowScore_endsAtRejected() {
        registerVerifyHistoryWithScore(400);

        ProcessInstance pi = start("APP-LOW", 5000);

        assertThat(pi)
                .hasPassed(
                        "Task_VerifyHistory",
                        "Task_EvaluateRules",
                        "EndEvent_Rejected")
                .hasNotPassed("Task_AutoApprove", "Task_ManualReview", "EndEvent_Approved")
                .isEnded()
                .variables()
                .containsEntry("dmnReason", "REJECTED_LOW_SCORE");
    }

    private void registerVerifyHistoryWithScore(int creditScore) {
        JavaDelegate stub = execution -> execution.setVariable("creditScore", creditScore);
        Mocks.register("verifyCreditHistoryDelegate", stub);
    }

    private ProcessInstance start(String applicantId, int requestedLimit) {
        return runtimeService.startProcessInstanceByKey(
                PROCESS_KEY,
                Map.of("applicantId", applicantId, "requestedLimit", requestedLimit)
        );
    }
}
