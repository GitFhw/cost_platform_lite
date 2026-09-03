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
 * 试算记录对象 cost_simulation_record
 *
 * <p>线程五的试算链路只写试算记录，不写正式结果台账。
 * 该表只有创建审计字段，没有更新审计字段，因此不继承 BaseEntity。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_simulation_record")
public class CostSimulationRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 试算记录主键
     */
    @TableId(value = "simulation_id", type = IdType.AUTO)
    private Long simulationId;

    /**
     * 场景主键
     */
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 发布版本主键
     */
    @TableField("version_id")
    private Long versionId;

    /**
     * 账期
     */
    @TableField("bill_month")
    private String billMonth;

    /**
     * 试算编号
     */
    @TableField("simulation_no")
    private String simulationNo;

    /**
     * 输入业务数据
     */
    @TableField("input_json")
    private String inputJson;

    /**
     * 变量计算结果
     */
    @TableField("variable_json")
    private String variableJson;

    /**
     * 解释结果
     */
    @TableField("explain_json")
    private String explainJson;

    /**
     * 试算结果
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 试算状态
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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
     * 版本号
     */
    @TableField(exist = false)
    private String versionNo;
}
