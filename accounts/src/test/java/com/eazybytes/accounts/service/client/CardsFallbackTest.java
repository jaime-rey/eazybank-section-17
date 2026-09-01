package com.eazybytes.accounts.service.client;

import com.eazybytes.accounts.dto.CardsDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CardsFallbackTest {

    private final CardsFallback fallback = new CardsFallback();

    @Test
    void fetchCardDetails_returnsNullAsFallbackContract() {
        ResponseEntity<CardsDto> result = fallback.fetchCardDetails("corr-1", "9345432123");
        assertThat(result).isNull();
    }
}
