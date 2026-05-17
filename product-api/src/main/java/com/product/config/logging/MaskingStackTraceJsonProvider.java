package com.product.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

public class MaskingStackTraceJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    public MaskingStackTraceJsonProvider() {
        setFieldName("stackTrace");
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (event.getThrowableProxy() == null) {
            return;
        }

        generator.writeStringField(getFieldName(), LogMasker.mask(ThrowableProxyUtil.asString(event.getThrowableProxy())));
    }
}
