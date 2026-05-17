package com.product.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

public class MaskingMessageJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    public MaskingMessageJsonProvider() {
        setFieldName("message");
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        generator.writeStringField(getFieldName(), LogMasker.mask(event.getFormattedMessage()));
    }
}
