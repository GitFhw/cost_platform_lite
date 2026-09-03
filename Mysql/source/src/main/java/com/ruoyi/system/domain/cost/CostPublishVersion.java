package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.annotation.Excel;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 发布版本对象 cost_publish_version
 *
 * <p>发布版本表只有 create/update 审计字段，没有 remark 字段，
 * 因此这里不继承 BaseEntity，避免默认 SQL 访问不存在的列。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_publish_version")
public class CostPublishVersion implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发布版本主键
     */
    @TableId(value = "version_id", type = IdType.AUTO)
    private Long versionId;

    /**
     * 所属场景主键
     */
    @TableField("scene_id")
    private Long sceneId;

    /**
     * 场景编码
     */
    @Excel(name = "场景编码")
    @TableField(exist = false)
    private String sceneCode;

    /**
     * 场景名称
     */
    @Excel(name = "场景名称")
    @TableField(exist = false)
    private String sceneName;

    /**
     * 业务域
     */
    @Excel(name = "业务域", dictType = "cost_business_domain")
    @TableField(exist = false)
    private String businessDomain;

    /**
     * 版本号
     */
    @Excel(name = "版本号")
    @TableField("version_no")
    @Size(max = 64, message = "版本号长度不能超过64个字符")
    private String versionNo;

    /**
     * 版本状态
     */
    @Excel(name = "版本状态", dictType = "cost_publish_version_status")
    @TableField("version_status")
    private String versionStatus;

    /**
     * 发布说明
     */
    @Excel(name = "发布说明")
    @TableField("publish_desc")
    @Size(max = 1000, message = "发布说明长度不能超过1000个字符")
    private String publishDesc;

    /**
     * 发布校验结果快照
     */
    @TableField("validation_result_json")
    private String validationResultJson;

    /**
     * 快照哈希
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 发布人
     */
    @Excel(name = "发布人")
    @TableField("published_by")
    private String publishedBy;

    /**
     * 发布时间
     */
    @Excel(name = "发布时间")
    @TableField("published_time")
    private Date publishedTime;

    /**
     * 生效操作人
     */
    @Excel(name = "生效操作人")
    @TableField("activated_by")
    private String activatedBy;

    /**
     * 生效时间
     */
    @Excel(name = "生效时间")
    @TableField("activated_time")
    private Date activatedTime;

    /**
     * 回滚操作人
     */
    @Excel(name = "回滚操作人")
    @TableField("rollback_by")
    private String rollbackBy;

    /**
     * 回滚时间
     */
    @Excel(name = "回滚时间")
    @TableField("rollback_time")
    private Date rollbackTime;

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
     * 更新人
     */
    @TableField("update_by")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;

    /**
     * 是否当前生效版本
     */
    @TableField(exist = false)
    private Integer activeFlag;

    /**
     * 上一个版本主键
     */
    @TableField(exist = false)
    private Long previousVersionId;

    /**
     * 上一个版本号
     */
    @TableField(exist = false)
    private String previousVersionNo;
}
