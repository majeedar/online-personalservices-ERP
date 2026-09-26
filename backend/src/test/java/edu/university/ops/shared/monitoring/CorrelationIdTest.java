package edu.university.ops.shared.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdTest {

    @Test
    void keepsSafeCallerSuppliedIds() {
        assertThat(CorrelationId.sanitizeOrGenerate("abc-123.X_y")).isEqualTo("abc-123.X_y");
    }

    @Test
    void replacesMissingOrUnsafeIds() {
        assertThat(CorrelationId.sanitizeOrGenerate(null)).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate("line\nbreak")).doesNotContain("\n");
        assertThat(CorrelationId.sanitizeOrGenerate("x".repeat(65))).hasSize(36);
    }
}
