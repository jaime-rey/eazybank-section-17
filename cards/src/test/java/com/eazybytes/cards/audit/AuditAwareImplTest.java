package com.eazybytes.cards.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAwareImplTest {

    @Test
    @DisplayName("getCurrentAuditor returns Optional containing 'CARDS_MS'")
    void getCurrentAuditor_returnsCardsMs() {
        AuditAwareImpl auditAware = new AuditAwareImpl();

        Optional<String> auditor = auditAware.getCurrentAuditor();

        assertThat(auditor).contains("CARDS_MS");
    }
}
