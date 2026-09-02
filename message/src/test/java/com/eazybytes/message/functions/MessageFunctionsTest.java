package com.eazybytes.message.functions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eazybytes.message.dto.AccountsMsgDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFunctionsTest {

    private static final AccountsMsgDto MSG = new AccountsMsgDto(
            1234567890L, "Ada Lovelace", "ada@example.com", "5550000001");

    private final MessageFunctions functions = new MessageFunctions();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(MessageFunctions.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void email_returnsSameDtoAndLogsInfo() {
        Function<AccountsMsgDto, AccountsMsgDto> email = functions.email();

        AccountsMsgDto result = email.apply(MSG);

        assertThat(result).isSameAs(MSG);
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Sending email")
                            .contains("1234567890")
                            .contains("ada@example.com");
                });
    }

    @Test
    void sms_returnsAccountNumberAndLogsInfo() {
        Function<AccountsMsgDto, Long> sms = functions.sms();

        Long result = sms.apply(MSG);

        assertThat(result).isEqualTo(1234567890L);
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Sending sms")
                            .contains("1234567890")
                            .contains("5550000001");
                });
    }
}
