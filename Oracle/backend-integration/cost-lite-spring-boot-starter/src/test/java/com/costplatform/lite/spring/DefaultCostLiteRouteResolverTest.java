package com.costplatform.lite.spring;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCostLiteRouteResolverTest {
    @Test
    void resolvesDefaultAndEncodedPathVariables() {
        DefaultCostLiteRouteResolver resolver = new DefaultCostLiteRouteResolver(new CostLiteSpringProperties());

        assertThat(resolver.resolve(CostLiteRouteKeys.HEALTH)).isEqualTo("/cost/lite/health");
        assertThat(resolver.resolve(
                CostLiteRouteKeys.SCENE_DELETE,
                Collections.<String, Object>singletonMap("sceneIds", "1,2")))
                .isEqualTo("/cost/scene/1%2C2");
    }

    @Test
    void supportsAHostSpecificUpstreamPathOverride() {
        CostLiteSpringProperties properties = new CostLiteSpringProperties();
        properties.setUpstreamPaths(Collections.singletonMap(
                CostLiteRouteKeys.HEALTH,
                "/billing/health"));

        DefaultCostLiteRouteResolver resolver = new DefaultCostLiteRouteResolver(properties);

        assertThat(resolver.resolve(CostLiteRouteKeys.HEALTH)).isEqualTo("/billing/health");
    }

    @Test
    void rejectsRoutesWithMissingVariables() {
        DefaultCostLiteRouteResolver resolver = new DefaultCostLiteRouteResolver(new CostLiteSpringProperties());

        assertThatThrownBy(() -> resolver.resolve(CostLiteRouteKeys.SCENE_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少路径变量");
    }
}
