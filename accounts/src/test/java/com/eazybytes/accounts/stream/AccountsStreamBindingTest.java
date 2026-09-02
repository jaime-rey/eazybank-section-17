package com.eazybytes.accounts.stream;

import com.eazybytes.accounts.dto.AccountsMsgDto;
import com.eazybytes.accounts.service.IAccountsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EnableTestBinder
class AccountsStreamBindingTest {

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private InputDestination input;

    @Autowired
    private OutputDestination output;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private IAccountsService accountsService;

    @Test
    void streamBridge_sendCommunicationOut_landsPayloadOnConfiguredDestination() throws Exception {
        AccountsMsgDto msg = new AccountsMsgDto(
                1234567890L,
                "Ada Lovelace",
                "ada@example.com",
                "5550000001");

        boolean sent = streamBridge.send("sendCommunication-out-0", msg);

        assertThat(sent).as("StreamBridge.send returned false — binding likely missing").isTrue();

        Message<byte[]> received = output.receive(2000, "sendCommunication-out-0");
        assertThat(received).as("no message on sendCommunication-out-0 within 2s").isNotNull();

        AccountsMsgDto payload = objectMapper.readValue(received.getPayload(), AccountsMsgDto.class);
        assertThat(payload).isEqualTo(msg);
    }

    @Test
    void updateCommunicationConsumer_receivesFromCommunicationSent_invokesService() {
        input.send(MessageBuilder.withPayload(1234567890L).build(), "updateCommunication-in-0");

        verify(accountsService, timeout(2000)).updateCommunicationStatus(1234567890L);
    }
}
