package com.costplatform.lite.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 根据是否提供专用 JDBC URL 选择计费数据源模式。
 *
 * <p>不能使用 {@code ConditionalOnProperty} 判断空字符串，因为宿主配置文件通常会保留
 * 一个空的占位属性。这里显式按去除空白后的值判断，保证两种模式互斥。</p>
 */
public final class CostLiteDataSourceModeCondition {
    private CostLiteDataSourceModeCondition() {
    }

    public static class Dedicated implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String jdbcUrl = context.getEnvironment().getProperty("cost.lite.datasource.url");
            return jdbcUrl != null && !jdbcUrl.trim().isEmpty();
        }
    }

    public static class Host implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String jdbcUrl = context.getEnvironment().getProperty("cost.lite.datasource.url");
            return jdbcUrl == null || jdbcUrl.trim().isEmpty();
        }
    }
}
