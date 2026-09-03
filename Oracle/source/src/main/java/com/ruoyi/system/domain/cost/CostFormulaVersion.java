package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 公式版本台账对象 cost_formula_version。
 *
 * <p>用于记录公式资产每次保存后的快照，支持历史版本回看与按版本重新装载。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_formula_version")
public class CostFormulaVersion implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本主键
     */
    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    /**
     * 公式主键
     */
    @TableField("formula_id")
    private Long formulaId;

    /**
     * 场景主键
     */
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 公式编码
     */
    @TableField("formula_code")
    private String formulaCode;

    /**
     * 公式名称
     */
    @TableField("formula_name")
    private String formulaName;

    /**
     * 资产类型
     */
    @TableField("asset_type")
    private String assetType;

    /**
     * 版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 变更类型
     */
    @TableField("change_type")
    private String changeType;

    /**
     * 业务中文公式
     */
    @TableField("business_formula")
    private String businessFormula;

    /**
     * 标准执行表达式
     */
    @TableField("formula_expr")
    private String formulaExpr;

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
     * 完整快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 创建人
     */
    @TableField("create_by")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;
}
