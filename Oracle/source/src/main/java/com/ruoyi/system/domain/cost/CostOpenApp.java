package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Date;
import java.util.List;

/**
 * 第三方开放应用对象 cost_open_app
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_open_app")
public class CostOpenApp extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(value = "app_id", type = IdType.AUTO)
    private Long appId;

    @TableField("app_code")
    private String appCode;

    @TableField("app_name")
    private String appName;

    /**
     * 仅用于后端认证，不对前端暴露。
     */
    @JsonIgnore
    @TableField("app_secret_hash")
    private String appSecretHash;

    @TableField("scene_scope_type")
    private String sceneScopeType;

    @TableField("scene_ids_json")
    private String sceneIdsJson;

    @TableField("allow_draft_snapshot")
    private Boolean allowDraftSnapshot;

    @TableField("token_ttl_seconds")
    private Integer tokenTtlSeconds;

    @TableField("effective_start_time")
    private Date effectiveStartTime;

    @TableField("effective_end_time")
    private Date effectiveEndTime;

    @TableField("status")
    private String status;

    @TableField(exist = false)
    private List<Long> sceneIds;

    @TableField(exist = false)
    private List<String> sceneNames;

    @TableField(exist = false)
    private String sceneNamesSummary;

    @TableField(exist = false)
    private Integer sceneCount;

    /**
     * 仅在新建或重置密钥成功后回传一次明文密钥。
     */
    @TableField(exist = false)
    private String appSecretPlaintext;
}
