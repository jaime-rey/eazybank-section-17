package com.eazybytes.accounts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilterInternal_reusesIncomingHeaderWhenPresent() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("existing-id");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), eq("existing-id"));
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void doFilterInternal_generatesUuidWhenHeaderIsMissingNullOrBlank(String header) throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(header);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), captor.capture());
        String generated = captor.getValue();
        assertThat(generated).isNotBlank();
        assertThat(generated).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilterInternal_clearsMdcEvenWhenChainThrows() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("cid-42");
        doThrowServletException(chain, request, response);

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException expected) {
            // expected
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private static void doThrowServletException(FilterChain chain, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        org.mockito.Mockito.doThrow(new ServletException("boom"))
                .when(chain).doFilter(any(), any());
    }
}
