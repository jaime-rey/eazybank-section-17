package com.eazybytes.message.functions;

import com.eazybytes.message.dto.AccountsMsgDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableTestBinder
class MessageFunctionsStreamTest {

    @Autowired
    private InputDestination input;

    @Autowired
    private OutputDestination output;

    @Test
    void composedEmailSms_pipesInputAndEmitsAccountNumberOnOutputBinding() {
        AccountsMsgDto msg = new AccountsMsgDto(
                1234567890L, "Ada Lovelace", "ada@example.com", "5550000001");

        input.send(MessageBuilder.withPayload(msg).build(), "send-communication");

        Message<byte[]> received = output.receive(2000, "communication-sent");

        assertThat(received).as("no message arrived at communication-sent within 2s").isNotNull();
        assertThat(new String(received.getPayload(), StandardCharsets.UTF_8))
                .isEqualTo("1234567890");
    }
}
