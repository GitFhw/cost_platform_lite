package com.costplatform.lite.starter;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

/**
 * 为嵌入式计费 Mapper 增加专用前缀，避免与宿主同名 Mapper 冲突。
 *
 * <p>业务系统通常也有 SysDictDataMapper、SysDictTypeMapper 等接口。
 * 计费 Mapper 使用 costLite 前缀后，两个 Mapper 扫描器可以共存，
 * 但 Mapper 接口本身的全限定名和 XML namespace 保持不变。</p>
 */
public final class CostLiteMapperBeanNameGenerator implements BeanNameGenerator {
    private static final String PREFIX = "costLite";

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String beanClassName = definition.getBeanClassName();
        if (beanClassName == null || beanClassName.isBlank()) {
            return PREFIX + "Mapper";
        }

        int packageSeparator = beanClassName.lastIndexOf('.');
        String shortClassName = packageSeparator >= 0
                ? beanClassName.substring(packageSeparator + 1)
                : beanClassName;
        if (shortClassName.isEmpty()) {
            return PREFIX + "Mapper";
        }
        return PREFIX + Character.toUpperCase(shortClassName.charAt(0)) + shortClassName.substring(1);
    }
}
