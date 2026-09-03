package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;

/**
 * 公式实验室主数据对象 cost_formula。
 *
 * <p>线程七开始将“公式”从变量和规则中的零散表达式提升为可治理的独立资产，
 * 后续变量中心、规则中心、发布快照和运行链均通过 formulaCode 进行引用。</p>
 *
 * @author HwFan
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_formula")
public class CostFormula extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 公式主键
     */
    @TableId(value = "formula_id", type = IdType.AUTO)
    private Long formulaId;

    /**
     * 所属场景主键
     */
    @NotNull(message = "所属场景不能为空")
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 场景编码
     */
    @TableField(exist = false)
    private String sceneCode;

    /**
     * 场景名称
     */
    @TableField(exist = false)
    private String sceneName;

    /**
     * 业务域
     */
    @TableField(exist = false)
    private String businessDomain;

    /**
     * 公式编码
     */
    @Excel(name = "公式编码")
    @NotBlank(message = "公式编码不能为空")
    @Size(max = 64, message = "公式编码长度不能超过64个字符")
    @TableField("formula_code")
    private String formulaCode;

    /**
     * 公式名称
     */
    @Excel(name = "公式名称")
    @NotBlank(message = "公式名称不能为空")
    @Size(max = 128, message = "公式名称长度不能超过128个字符")
    @TableField("formula_name")
    private String formulaName;

    /**
     * 公式用途说明
     */
    @Excel(name = "公式说明")
    @Size(max = 500, message = "公式说明长度不能超过500个字符")
    @TableField("formula_desc")
    private String formulaDesc;

    /**
     * 业务中文公式
     */
    @Excel(name = "业务中文公式")
    @Size(max = 1000, message = "业务中文公式长度不能超过1000个字符")
    @TableField("business_formula")
    private String businessFormula;

    /**
     * 标准执行表达式
     */
    @Excel(name = "标准表达式")
    @NotBlank(message = "标准执行表达式不能为空")
    @Size(max = 2000, message = "标准执行表达式长度不能超过2000个字符")
    @TableField("formula_expr")
    private String formulaExpr;

    /**
     * 资产类型
     */
    @Excel(name = "资产类型")
    @TableField("asset_type")
    private String assetType;

    /**
     * 工作台模式
     */
    @TableField("workbench_mode")
    private String workbenchMode;

    /**
     * 工作台结构类型
     */
    @TableField("workbench_pattern")
    private String workbenchPattern;

    /**
     * 工作台模板编码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 工作台点选配置
     */
    @TableField("workbench_config_json")
    private String workbenchConfigJson;

    /**
     * 命名空间范围
     */
    @Excel(name = "命名空间范围")
    @Size(max = 128, message = "命名空间范围长度不能超过128个字符")
    @TableField("namespace_scope")
    private String namespaceScope;

    /**
     * 返回类型
     */
    @Excel(name = "返回类型", dictType = "cost_formula_return_type")
    @Size(max = 32, message = "返回类型长度不能超过32个字符")
    @TableField("return_type")
    private String returnType;

    /**
     * 测试样例上下文
     */
    @TableField("test_case_json")
    private String testCaseJson;

    /**
     * 最近测试结果样例
     */
    @TableField("sample_result_json")
    private String sampleResultJson;

    /**
     * 最近测试时间
     */
    @TableField("last_test_time")
    private Date lastTestTime;

    /**
     * 状态
     */
    @Excel(name = "状态", dictType = "cost_formula_status")
    @NotBlank(message = "公式状态不能为空")
    @TableField("status")
    private String status;

    /**
     * 排序号
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 变量引用数量
     */
    @TableField(exist = false)
    private Long variableRefCount;

    /**
     * 规则引用数量
     */
    @TableField(exist = false)
    private Long ruleRefCount;

    /**
     * 发布快照引用数量
     */
    @TableField(exist = false)
    private Long publishedVersionCount;

    /**
     * 当前版本号
     */
    @TableField(exist = false)
    private Integer currentVersionNo;
}
