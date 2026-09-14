package com.example.apigateway;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PipelineAuditFailureTest {

    @Test
    void controlledFailureIsDisabled() {
        assertNotEquals(
                "true",
                System.getenv("FORCE_TEST_FAILURE"),
                "Intentional audit test failure requested through FORCE_TEST_FAILURE.");
    }
}
