package com.costplatform.lite.spring;

import com.costplatform.lite.client.CostLiteClient;
import com.costplatform.lite.client.CostLiteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostLiteSpringBootAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CostLiteSpringBootAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void doesNotChangeHostWhenIntegrationIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CostLiteClient.class);
            assertThat(context).doesNotHaveBean(CostLiteProxyController.class);
        });
    }

    @Test
    void createsClientAndProxyWhenIntegrationIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "cost.lite.integration.enabled=true",
                        "cost.lite.integration.base-url=http://127.0.0.1:18080",
                        "cost.lite.integration.web-path=/cost-lite")
                .run(context -> {
                    assertThat(context).hasSingleBean(CostLiteClient.class);
                    assertThat(context).hasSingleBean(CostLiteRouteResolver.class);
                    assertThat(context).hasSingleBean(CostLiteProxyController.class);
                    assertThat(context.getBean(CostLiteSpringProperties.class).getWebPath())
                            .isEqualTo("/cost-lite");
                });
    }

    @Test
    void allowsHostToDisableOnlyTheProxyController() {
        contextRunner
                .withPropertyValues(
                        "cost.lite.integration.enabled=true",
                        "cost.lite.integration.base-url=http://127.0.0.1:18080",
                        "cost.lite.integration.proxy-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CostLiteClient.class);
                    assertThat(context).doesNotHaveBean(CostLiteProxyController.class);
        });
    }

    @Test
    void preservesUnsafeLongIdentifiersWhenNormalizingProxyResponses() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CostLiteSpringProperties properties = new CostLiteSpringProperties();
        CostLiteProxyResponseFactory factory = new CostLiteProxyResponseFactory(objectMapper, properties);
        CostLiteResponse response = new CostLiteResponse(
                200,
                "{\"code\":200,\"total\":1,\"rows\":[{\"resultId\":2095090130307379203,\"amountValue\":12.50}]}",
                objectMapper.readTree("{\"code\":200,\"total\":1,\"rows\":[{\"resultId\":2095090130307379203,\"amountValue\":12.50}]}")
        );

        Map<?, ?> normalized = (Map<?, ?>) factory.success(response);
        Map<?, ?> data = (Map<?, ?>) normalized.get("data");
        List<?> rows = (List<?>) data.get("rows");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);

        assertThat(row.get("resultId")).isEqualTo("2095090130307379203");
        assertThat(row.get("amountValue")).isInstanceOf(Number.class);
        assertThat(((Number) row.get("amountValue")).doubleValue()).isEqualTo(12.5D);
    }
}
