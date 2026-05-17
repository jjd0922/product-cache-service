package com.product.presentation.common.filter;

import jakarta.servlet.FilterChain;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private Tracer tracer;
    private BaggageInScope baggageInScope;
    private ObjectProvider<Tracer> tracerProvider;
    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        baggageInScope = mock(BaggageInScope.class);
        tracerProvider = mock(ObjectProvider.class);
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.createBaggageInScope(anyString(), anyString())).thenReturn(baggageInScope);
        filter = new RequestIdFilter(tracerProvider);
    }

    @Test
    void doFilterInternal_whenRequestIdHeaderExists_thenPropagateHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products/1");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get(RequestIdFilter.USER_ID_MDC_KEY)).isNull();
        verify(tracer).createBaggageInScope(RequestIdFilter.MDC_KEY, "request-123");
        verify(baggageInScope).close();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_whenRequestIdHeaderIsMissing_thenGenerateHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get(RequestIdFilter.USER_ID_MDC_KEY)).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
