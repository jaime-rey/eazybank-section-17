package com.eazybytes.accounts.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAwareImplTest {

    @Test
    @DisplayName("getCurrentAuditor returns Optional containing 'ACCOUNTS_MS'")
    void getCurrentAuditor_returnsAccountsMs() {
        AuditAwareImpl auditAware = new AuditAwareImpl();

        Optional<String> auditor = auditAware.getCurrentAuditor();

        assertThat(auditor).contains("ACCOUNTS_MS");
    }
}
