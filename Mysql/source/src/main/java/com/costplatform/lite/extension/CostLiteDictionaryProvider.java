package com.costplatform.lite.extension;

/**
 * 宿主可选提供的轻量计费字典校验适配器。
 *
 * <p>该接口是稳定的公开扩展点，不暴露母体内部包名。只有将
 * {@code cost.lite.dictionary.provider} 设置为 {@code CUSTOM} 时才会启用。</p>
 */
public interface CostLiteDictionaryProvider {
    /**
     * 判断字典类型是否存在。
     *
     * @param dictType 计费统一字典类型
     * @return 字典类型是否存在
     */
    boolean containsType(String dictType);

    /**
     * 判断字典值是否存在且可用。
     *
     * @param dictType 计费统一字典类型
     * @param dictValue 待校验的统一字典值
     * @return 字典值是否可用
     */
    boolean containsValue(String dictType, String dictValue);
}
