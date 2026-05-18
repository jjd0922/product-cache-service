package com.product.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaskingStackTraceJsonProviderTest {

    @Test
    void writeTo_writesMaskedStackTrace() throws Exception {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(new ThrowableProxy(new RuntimeException("email=user@example.com")));
        MaskingStackTraceJsonProvider provider = new MaskingStackTraceJsonProvider();

        StringWriter writer = new StringWriter();
        var generator = new JsonFactory().createGenerator(writer);

        generator.writeStartObject();
        provider.writeTo(generator, event);
        generator.writeEndObject();
        generator.close();

        assertThat(writer.toString()).contains("\"stackTrace\"");
        assertThat(writer.toString()).doesNotContain("user@example.com");
    }
}
