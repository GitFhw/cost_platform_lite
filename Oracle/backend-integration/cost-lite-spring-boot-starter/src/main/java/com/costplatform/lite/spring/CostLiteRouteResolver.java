package com.costplatform.lite.spring;

import java.util.Collections;
import java.util.Map;

/**
 * 将稳定的轻量计费语义路由键解析为宿主实际调用的相对路径。
 */
public interface CostLiteRouteResolver {
    String resolve(String routeKey, Map<String, ?> variables);

    default String resolve(String routeKey) {
        return resolve(routeKey, Collections.<String, Object>emptyMap());
    }
}
