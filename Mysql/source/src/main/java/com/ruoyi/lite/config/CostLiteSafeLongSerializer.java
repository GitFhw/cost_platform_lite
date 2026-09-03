package com.ruoyi.lite.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 只把超出 JavaScript 安全整数范围的 Long 输出为字符串，避免前端丢失 Snowflake 主键精度。
 */
public final class CostLiteSafeLongSerializer extends JsonSerializer<Long> {
    public static final CostLiteSafeLongSerializer INSTANCE = new CostLiteSafeLongSerializer();
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private CostLiteSafeLongSerializer() {
    }

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            generator.writeString(value.toString());
            return;
        }
        generator.writeNumber(value);
    }
}
