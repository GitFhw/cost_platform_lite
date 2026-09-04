package com.ruoyi.system.domain.cost.bo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 试算执行请求
 *
 * @author HwFan
 */
@Data
public class CostSimulationExecuteBo {
    /**
     * 场景主键
     */
    @NotNull(message = "试算场景不能为空")
    private Long sceneId;

    /**
     * 发布版本主键
     */
    private Long versionId;

    /**
     * 指定试算费目；不传时按场景全部费目试算。
     */
    private Long feeId;

    /**
     * 指定多个试算费目；与 feeId、feeCode 一起使用时会合并去重。
     */
    private List<Long> feeIds;

    /**
     * 按费目编码指定试算范围。
     */
    private String feeCode;

    /**
     * 账期，格式 yyyy-MM
     */
    private String billMonth;

    /**
     * 输入业务数据 JSON
     */
    @NotBlank(message = "试算输入数据不能为空")
    private String inputJson;

    /**
     * 是否返回规则、要素和定价解释，供配置联调定位问题。
     */
    private Boolean includeExplain;
}
