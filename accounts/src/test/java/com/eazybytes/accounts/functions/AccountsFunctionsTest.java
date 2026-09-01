package com.eazybytes.accounts.functions;

import com.eazybytes.accounts.service.IAccountsService;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountsFunctionsTest {

    private final AccountsFunctions functions = new AccountsFunctions();

    @Test
    void updateCommunication_delegatesAccountNumberToService() {
        IAccountsService accountsService = mock(IAccountsService.class);
        Consumer<Long> consumer = functions.updateCommunication(accountsService);

        consumer.accept(1234567890L);

        verify(accountsService).updateCommunicationStatus(1234567890L);
    }
}
