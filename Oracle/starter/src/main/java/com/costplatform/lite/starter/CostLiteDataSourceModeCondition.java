package com.costplatform.lite.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 根据显式模式选择计费数据源；未配置模式时保留旧版按 JDBC URL 推断的兼容行为。
 *
 * <p>不能使用 {@code ConditionalOnProperty} 判断空字符串，因为宿主配置文件通常会保留
 * 一个空的占位属性。这里显式按去除空白后的值判断，保证两种模式互斥。</p>
 */
public final class CostLiteDataSourceModeCondition {
    private static final String MODE_PROPERTY = "cost.lite.datasource.mode";
    private static final String MODE_DEDICATED = "dedicated";
    private static final String MODE_HOST = "host";

    private CostLiteDataSourceModeCondition() {
    }

    public static class Dedicated implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = mode(context);
            if (MODE_DEDICATED.equals(mode)) {
                return true;
            }
            if (MODE_HOST.equals(mode)) {
                return false;
            }
            String jdbcUrl = context.getEnvironment().getProperty("cost.lite.datasource.url");
            return jdbcUrl != null && !jdbcUrl.trim().isEmpty();
        }
    }

    public static class Host implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = mode(context);
            if (MODE_HOST.equals(mode)) {
                return true;
            }
            if (MODE_DEDICATED.equals(mode)) {
                return false;
            }
            String jdbcUrl = context.getEnvironment().getProperty("cost.lite.datasource.url");
            return jdbcUrl == null || jdbcUrl.trim().isEmpty();
        }
    }

    private static String mode(ConditionContext context) {
        String configured = context.getEnvironment().getProperty(MODE_PROPERTY, "");
        String normalized = configured == null ? "" : configured.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.isEmpty() && !MODE_DEDICATED.equals(normalized) && !MODE_HOST.equals(normalized)) {
            throw new IllegalStateException("cost.lite.datasource.mode 必须是 dedicated 或 host");
        }
        return normalized;
    }
}
