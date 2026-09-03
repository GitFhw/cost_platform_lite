package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 费用详情中的结果台账引用。
 *
 * @author HwFan
 */
@Data
public class CostFeeResultRefVo {
    private Long resultId;
    private Long taskId;
    private Long versionId;
    private String versionNo;
    private String taskNo;
    private String bizNo;
    private String billMonth;
    private String objectDimension;
    private String objectCode;
    private String objectName;
    private BigDecimal quantityValue;
    private BigDecimal unitPrice;
    private BigDecimal amountValue;
    private String currencyCode;
    private String resultStatus;
    private Date createTime;
}
