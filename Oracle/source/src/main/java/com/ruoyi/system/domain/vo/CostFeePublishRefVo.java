package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 费用详情中的发布版本引用。
 *
 * @author HwFan
 */
@Data
public class CostFeePublishRefVo {
    private Long versionId;
    private Long sceneId;
    private String versionNo;
    private String versionStatus;
    private String publishDesc;
    private String publishedBy;
    private Date publishedTime;
}
