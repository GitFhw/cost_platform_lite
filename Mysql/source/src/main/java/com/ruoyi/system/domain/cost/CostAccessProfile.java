package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * 数据接入方案对象 cost_access_profile
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("cost_access_profile")
public class CostAccessProfile extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @TableId(value = "profile_id", type = IdType.AUTO)
    private Long profileId;

    @TableField("scene_id")
    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    @TableField("fee_id")
    private Long feeId;

    @TableField("fee_scope_type")
    private String feeScopeType;

    @TableField("fee_ids_json")
    private String feeIdsJson;

    @TableField("version_id")
    private Long versionId;

    @TableField("profile_code")
    @NotBlank(message = "方案编码不能为空")
    @Size(max = 64, message = "方案编码长度不能超过64个字符")
    private String profileCode;

    @TableField("profile_name")
    @NotBlank(message = "方案名称不能为空")
    @Size(max = 128, message = "方案名称长度不能超过128个字符")
    private String profileName;

    @TableField("source_type")
    @NotBlank(message = "来源类型不能为空")
    @Size(max = 32, message = "来源类型长度不能超过32个字符")
    private String sourceType;

    @TableField("task_type")
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 32, message = "任务类型长度不能超过32个字符")
    private String taskType;

    @TableField("request_method")
    @Size(max = 16, message = "请求方法长度不能超过16个字符")
    private String requestMethod;

    @TableField("endpoint_url")
    @Size(max = 255, message = "接口地址长度不能超过255个字符")
    private String endpointUrl;

    @TableField("auth_type")
    @Size(max = 32, message = "鉴权方式长度不能超过32个字符")
    private String authType;

    @TableField("auth_config_json")
    private String authConfigJson;

    @TableField("fetch_config_json")
    private String fetchConfigJson;

    @TableField("mapping_json")
    private String mappingJson;

    @TableField("sample_payload_json")
    private String samplePayloadJson;

    @TableField("sample_input_json")
    private String sampleInputJson;

    @TableField("status")
    @NotBlank(message = "状态不能为空")
    private String status;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField(exist = false)
    private String keyword;

    @TableField(exist = false)
    private String sceneCode;

    @TableField(exist = false)
    private String sceneName;

    @TableField(exist = false)
    private String feeCode;

    @TableField(exist = false)
    private String feeName;

    @TableField(exist = false)
    private List<Long> feeIds;

    @TableField(exist = false)
    private String versionNo;
}
