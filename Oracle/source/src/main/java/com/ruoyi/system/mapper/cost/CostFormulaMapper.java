package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostFormula;
import com.ruoyi.system.domain.vo.CostFormulaGovernanceCheckVo;

import java.util.List;
import java.util.Map;

/**
 * 公式实验室 Mapper 接口。
 *
 * <p>线程七将公式抽为可治理资产，变量、规则、发布快照和运行链都通过公式编码引用。</p>
 *
 * @author HwFan
 */
public interface CostFormulaMapper extends BaseMapper<CostFormula> {
    /**
     * 查询公式列表。
     */
    List<CostFormula> selectFormulaList(CostFormula formula);

    /**
     * 查询公式选择框。
     */
    List<CostFormula> selectFormulaOptions(CostFormula formula);

    /**
     * 查询模板库选择项。
     */
    List<CostFormula> selectTemplateOptions(CostFormula formula);

    /**
     * 查询公式统计。
     */
    Map<String, Object> selectFormulaStats(CostFormula formula);

    /**
     * 查询公式治理检查结果。
     */
    CostFormulaGovernanceCheckVo selectFormulaGovernanceCheck(Long formulaId);
}
