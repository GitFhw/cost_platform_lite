package com.ruoyi.system.service.cost.publish.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

import java.util.LinkedHashMap;

public final class PublishJsonSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PublishJsonSupport() {
    }

    public static LinkedHashMap<String, Object> parseJsonMap(String json) {
        if (StringUtils.isEmpty(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new ServiceException("快照 JSON 解析失败");
        }
    }

    public static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("快照 JSON 序列化失败");
        }
    }

    public static String canonicalJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("对象序列化失败");
        }
    }
}
