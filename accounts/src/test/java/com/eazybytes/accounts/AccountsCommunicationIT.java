package com.eazybytes.accounts;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.AccountsMsgDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"send-communication", "communication-sent"})
@ActiveProfiles("kafkatest")
@TestPropertySource(properties = "spring.cloud.stream.kafka.binder.brokers=${spring.embedded.kafka.brokers}")
@TestConstructor(autowireMode = ALL)
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class AccountsCommunicationIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TestRestTemplate restTemplate;
    private final CustomerRepository customerRepository;
    private final AccountsRepository accountsRepository;
    private final EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, byte[]> consumer;
    private Producer<String, byte[]> producer;

    @BeforeEach
    void setupKafkaClients() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            embeddedKafkaBroker, "communication-it-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(
            consumerProps, new StringDeserializer(), new ByteArrayDeserializer()
        ).createConsumer();
        consumer.subscribe(List.of("send-communication"));

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<>(
            producerProps, new StringSerializer(), new ByteArraySerializer()
        ).createProducer();
    }

    @AfterEach
    void closeKafkaClients() {
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
    }

    @Test
    void communicationRoundTrip_updatesFlag() throws Exception {
        // 1. Create customer + account via HTTP
        String mobileNumber = "9998887777";
        CustomerDto customerDto = new CustomerDto();
        customerDto.setName("Ada Lovelace");
        customerDto.setEmail("ada@example.com");
        customerDto.setMobileNumber(mobileNumber);
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountType("Savings");
        accountsDto.setBranchAddress("123 Main Street");
        customerDto.setAccountsDto(accountsDto);

        ResponseEntity<Void> createResponse =
            restTemplate.postForEntity("/api/create", customerDto, Void.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Locate the created account in DB
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow();
        Accounts account = accountsRepository
            .findByCustomer_CustomerId(customer.getCustomerId()).orElseThrow();
        Long accountNumber = account.getAccountNumber();

        // 3. Consume the outbound message from send-communication
        ConsumerRecord<String, byte[]> record = KafkaTestUtils.getSingleRecord(
            consumer, "send-communication", Duration.ofSeconds(10));

        // 4. Deserialize and assert payload
        AccountsMsgDto payload = OBJECT_MAPPER.readValue(record.value(), AccountsMsgDto.class);
        assertThat(payload.accountNumber()).isEqualTo(accountNumber);
        assertThat(payload.name()).isEqualTo("Ada Lovelace");
        assertThat(payload.email()).isEqualTo("ada@example.com");
        assertThat(payload.mobileNumber()).isEqualTo(mobileNumber);

        // 5. Publish the ack back to communication-sent
        byte[] ackPayload = String.valueOf(accountNumber).getBytes(UTF_8);
        producer.send(new ProducerRecord<>("communication-sent", ackPayload)).get();
        producer.flush();

        // 6. Await until communicationSw becomes true
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> {
                Accounts refreshed = accountsRepository
                    .findByCustomer_CustomerId(customer.getCustomerId()).orElseThrow();
                assertThat(refreshed.getCommunicationSw()).isTrue();
            });
    }
}
