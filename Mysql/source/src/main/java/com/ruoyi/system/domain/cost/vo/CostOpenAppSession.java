package com.ruoyi.system.domain.cost.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 开放接口访问会话
 */
@Data
public class CostOpenAppSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long appId;

    private String appCode;

    private String appName;

    private String sceneScopeType;

    private List<Long> authorizedSceneIds = new ArrayList<>();

    private Boolean allowDraftSnapshot;

    private Integer tokenTtlSeconds;

    private Date issuedAt;

    private Date expiresAt;
}
