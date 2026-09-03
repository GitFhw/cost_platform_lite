package com.ruoyi.system.domain.cost;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 发布快照对象 cost_publish_snapshot
 *
 * <p>发布快照表只保留创建审计字段，不包含 BaseEntity 默认的更新审计列，
 * 因此这里不继承 BaseEntity，避免 MyBatis-Plus 在插入时拼接不存在的 update_by/update_time 字段。</p>
 *
 * @author HwFan
 */
@Data
@TableName("cost_publish_snapshot")
public class CostPublishSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 快照明细主键
     */
    @TableId(value = "snapshot_id", type = IdType.AUTO)
    private Long snapshotId;

    /**
     * 所属发布版本主键
     */
    @TableField("version_id")
    private Long versionId;

    /**
     * 快照对象类型
     */
    @TableField("snapshot_type")
    @Size(max = 32, message = "快照类型长度不能超过32个字符")
    private String snapshotType;

    /**
     * 对象编码
     */
    @TableField("object_code")
    @Size(max = 64, message = "对象编码长度不能超过64个字符")
    private String objectCode;

    /**
     * 对象名称
     */
    @TableField("object_name")
    @Size(max = 128, message = "对象名称长度不能超过128个字符")
    private String objectName;

    /**
     * 快照 JSON
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 排序号
     */
    @TableField("sort_no")
    private Integer sortNo;

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
}
