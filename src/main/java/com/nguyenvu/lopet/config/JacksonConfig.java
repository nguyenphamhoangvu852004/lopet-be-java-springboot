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

/**
 * Giữ nguyên cách {@code JSON.stringify} của Node serialize một {@code Date}:
 * {@code "2026-08-10T05:12:33.000Z"} — luôn UTC, luôn đúng 3 chữ số mili giây, luôn hậu tố Z.
 *
 * <p>Cột trong DB là {@code datetime(6)} không mang timezone; driver mysql của Node đọc nó theo
 * timezone của tiến trình rồi mới đổi sang UTC lúc serialize. Chuyển đổi ở đây tái hiện đúng dãy
 * đó: LocalDateTime → zone hệ thống → UTC. Mặc định của Jackson là ISO không có offset, tức là
 * client sẽ hiểu sai múi giờ nếu không ghi đè.
 *
 * <p>Customizer này chỉ chạm tới ObjectMapper của Spring (Jackson 3, {@code tools.jackson}).
 * netty-socketio mang theo Jackson 2 riêng của nó và không thấy cấu hình này, nên
 * {@link com.nguyenvu.lopet.realtime.SocketIoConfig} phải cắm lại cùng định dạng — cả hai đường
 * đều đi qua {@link #nodeIso(LocalDateTime)} để không bao giờ lệch nhau.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter NODE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /** Nguồn duy nhất của định dạng ngày trả cho client, dùng chung cho REST lẫn Socket.IO */
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
