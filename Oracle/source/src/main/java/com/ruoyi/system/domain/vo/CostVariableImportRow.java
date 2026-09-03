package com.ruoyi.system.domain.vo;

import com.ruoyi.common.annotation.Excel;
import lombok.Data;

/**
 * 变量中心导入行模型。
 *
 * <p>线程二要求变量中心支持 Excel 模板导入与预览，因此单独定义导入模板对象，避免直接暴露内部主键字段。</p>
 *
 * @author HwFan
 */
@Data
public class CostVariableImportRow {
    @Excel(name = "场景编码")
    private String sceneCode;

    @Excel(name = "变量分组编码")
    private String groupCode;

    @Excel(name = "变量编码")
    private String variableCode;

    @Excel(name = "变量名称")
    private String variableName;

    @Excel(name = "变量类型")
    private String variableType;

    @Excel(name = "来源类型")
    private String sourceType;

    @Excel(name = "来源系统")
    private String sourceSystem;

    @Excel(name = "字典类型")
    private String dictType;

    @Excel(name = "第三方接口")
    private String remoteApi;

    @Excel(name = "鉴权方式")
    private String authType;

    @Excel(name = "鉴权配置JSON")
    private String authConfigJson;

    @Excel(name = "上下文路径")
    private String dataPath;

    @Excel(name = "字段映射JSON")
    private String mappingConfigJson;

    @Excel(name = "同步方式")
    private String syncMode;

    @Excel(name = "缓存策略")
    private String cachePolicy;

    @Excel(name = "失败兜底")
    private String fallbackPolicy;

    @Excel(name = "公式表达式")
    private String formulaExpr;

    @Excel(name = "数据类型")
    private String dataType;

    @Excel(name = "默认值")
    private String defaultValue;

    @Excel(name = "精度")
    private Integer precisionScale;

    @Excel(name = "状态")
    private String status;

    @Excel(name = "排序号")
    private Integer sortNo;

    @Excel(name = "备注")
    private String remark;
}
