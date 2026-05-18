package com.product.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaskingMessageJsonProviderTest {

    @Test
    void writeTo_writesMaskedMessageField() throws Exception {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("send email to user@example.com");
        MaskingMessageJsonProvider provider = new MaskingMessageJsonProvider();

        StringWriter writer = new StringWriter();
        var generator = new JsonFactory().createGenerator(writer);

        generator.writeStartObject();
        provider.writeTo(generator, event);
        generator.writeEndObject();
        generator.close();

        assertThat(writer.toString()).contains("\"message\":\"send email to ***\"");
        assertThat(writer.toString()).doesNotContain("user@example.com");
    }
}
