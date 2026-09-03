package com.ruoyi.system.service.cost.dictionary;

/**
 * 计费字典提供器。
 *
 * <p>母体默认对接若依系统字典，轻量运行时或业务系统可以提供自己的实现，
 * 从而避免计费服务直接依赖某一种系统字典表结构。</p>
 */
public interface CostDictionaryProvider {
    /**
     * 判断字典类型是否存在。
     *
     * @param dictType 计费平台使用的统一字典类型
     * @return 是否存在
     */
    boolean containsType(String dictType);

    /**
     * 判断字典值是否存在且可用。
     *
     * @param dictType  计费平台使用的统一字典类型
     * @param dictValue 待校验值
     * @return 是否存在且可用
     */
    boolean containsValue(String dictType, String dictValue);
}
