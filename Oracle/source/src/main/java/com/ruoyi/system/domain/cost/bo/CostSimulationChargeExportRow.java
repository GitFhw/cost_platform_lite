package com.ruoyi.system.domain.cost.bo;

import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试算计费明细导出行。
 *
 * @author HwFan
 */
@Data
public class CostSimulationChargeExportRow {
    @Excel(name = "试算编号")
    private String simulationNo;

    @Excel(name = "场景编码")
    private String sceneCode;

    @Excel(name = "场景名称")
    private String sceneName;

    @Excel(name = "版本号")
    private String versionNo;

    @Excel(name = "账期")
    private String billMonth;

    @Excel(name = "业务编号")
    private String bizNo;

    @Excel(name = "核算对象编码")
    private String objectCode;

    @Excel(name = "核算对象名称")
    private String objectName;

    @Excel(name = "费用编码")
    private String feeCode;

    @Excel(name = "费用名称")
    private String feeName;

    @Excel(name = "计价单位")
    private String unitCode;

    @Excel(name = "命中规则")
    private String ruleCode;

    @Excel(name = "规则名称")
    private String ruleName;

    @Excel(name = "数量")
    private BigDecimal quantityValue;

    @Excel(name = "单价")
    private BigDecimal unitPrice;

    @Excel(name = "金额")
    private BigDecimal amountValue;

    @Excel(name = "定价来源")
    private String pricingSource;

    @Excel(name = "数量来源")
    private String quantitySource;

    @Excel(name = "单价来源")
    private String unitPriceSource;

    @Excel(name = "计价说明")
    private String pricingSummary;
}
