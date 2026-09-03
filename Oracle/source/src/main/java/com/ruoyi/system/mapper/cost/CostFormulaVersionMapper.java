package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostFormulaVersion;

import java.util.List;

/**
 * 公式版本台账 Mapper。
 *
 * @author HwFan
 */
public interface CostFormulaVersionMapper extends BaseMapper<CostFormulaVersion> {
    /**
     * 查询公式版本台账。
     */
    List<CostFormulaVersion> selectFormulaVersionList(Long formulaId);
}
