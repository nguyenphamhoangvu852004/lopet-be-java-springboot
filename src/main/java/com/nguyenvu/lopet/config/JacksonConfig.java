package com.nguyenvu.lopet.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter NODE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static String nodeIso(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("UTC"))
                .format(NODE_ISO);
    }

    @Bean
    public JsonMapperBuilderCustomizer lopetDateFormatCustomizer() {
        SimpleModule module = new SimpleModule("lopet-node-dates");
        module.addSerializer(LocalDateTime.class, new NodeDateSerializer());
        return builder -> builder.addModule(module);
    }

    private static final class NodeDateSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(nodeIso(value));
        }
    }
}
