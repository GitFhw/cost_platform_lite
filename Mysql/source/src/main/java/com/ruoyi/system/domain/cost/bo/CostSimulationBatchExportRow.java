package com.ruoyi.system.domain.cost.bo;

import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 批量试算回归摘要导出行。
 *
 * @author HwFan
 */
@Data
public class CostSimulationBatchExportRow {
    @Excel(name = "业务编号")
    private String bizNo;

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

    @Excel(name = "试算状态", dictType = "cost_simulation_status")
    private String status;

    @Excel(name = "总金额")
    private BigDecimal amountTotal;

    @Excel(name = "计费明细数")
    private Integer chargeLineCount;

    @Excel(name = "异常信息")
    private String errorMessage;

    @Excel(name = "执行时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date simulationTime;
}
