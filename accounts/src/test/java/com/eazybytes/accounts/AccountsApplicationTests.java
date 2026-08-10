package com.eazybytes.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"communication-sent", "send-communication"})
class AccountsApplicationTests {

	@Test
	void contextLoads() {
	}

}