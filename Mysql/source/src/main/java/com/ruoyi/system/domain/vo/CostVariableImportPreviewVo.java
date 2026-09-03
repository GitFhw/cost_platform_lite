package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 变量导入预览结果。
 *
 * @author HwFan
 */
@Data
public class CostVariableImportPreviewVo {
    /**
     * 总行数
     */
    private Integer totalRows = 0;

    /**
     * 校验通过行数
     */
    private Integer passRows = 0;

    /**
     * 校验失败行数
     */
    private Integer failRows = 0;

    /**
     * 是否可以继续导入
     */
    private Boolean importable = Boolean.FALSE;

    /**
     * 预览数据
     */
    private List<Map<String, Object>> previewRows = new ArrayList<>();

    /**
     * 校验问题
     */
    private List<CostVariableImportIssueVo> issues = new ArrayList<>();
}
