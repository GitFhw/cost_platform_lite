package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.domain.cost.CostFeeVariableRel;
import com.ruoyi.system.domain.vo.CostFeeGovernanceCheckVo;
import com.ruoyi.system.domain.vo.CostFeePublishRefVo;
import com.ruoyi.system.domain.vo.CostFeeResultRefVo;
import com.ruoyi.system.domain.vo.CostFeeRuleSummaryVo;
import com.ruoyi.system.domain.vo.CostFeeVariableContractVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 费用中心Mapper接口
 *
 * @author HwFan
 */
public interface CostFeeMapper extends BaseMapper<CostFeeItem> {
    /**
     * 查询费用列表
     *
     * @param feeItem 查询对象
     *
     * @return 费用集合
     */
    List<CostFeeItem> selectFeeList(CostFeeItem feeItem);

    /**
     * 查询费用选择框
     *
     * @param feeItem 查询对象
     *
     * @return 费用集合
     */
    List<CostFeeItem> selectFeeOptions(CostFeeItem feeItem);

    /**
     * 查询费用统计
     *
     * @param feeItem 查询对象
     *
     * @return 统计结果
     */
    Map<String, Object> selectFeeStats(CostFeeItem feeItem);

    /**
     * 查询费用治理预检查结果
     *
     * @param feeId 费用主键
     *
     * @return 结果
     */
    CostFeeGovernanceCheckVo selectFeeGovernanceCheck(Long feeId);

    /**
     * Query the variable input contract for a fee.
     *
     * @param feeId fee id
     *
     * @return contract items
     */
    List<CostFeeVariableContractVo> selectFeeVariableContracts(Long feeId);

    /**
     * Query linked rules for a fee detail drawer.
     *
     * @param feeId fee id
     *
     * @return rule summaries
     */
    List<CostFeeRuleSummaryVo> selectFeeRuleSummaries(Long feeId);

    /**
     * Query publish references for a fee detail drawer.
     *
     * @param feeId fee id
     *
     * @return publish references
     */
    List<CostFeePublishRefVo> selectFeePublishRefs(Long feeId);

    /**
     * Query recent result ledger references for a fee detail drawer.
     *
     * @param feeId fee id
     *
     * @return result references
     */
    List<CostFeeResultRefVo> selectFeeResultRefs(Long feeId);

    int insertFeeVariableRels(@Param("list") List<CostFeeVariableRel> list);

    /**
     * 按费用主键批量删除规则派生的费用-变量关系。
     *
     * @param feeIds 费用主键数组
     *
     * @return 删除条数
     */
    int deleteRuleDerivedFeeVariableRelByFeeIds(Long[] feeIds);

    /**
     * 按费用主键批量删除费用-变量关系。
     *
     * @param feeIds 费用主键数组
     *
     * @return 删除条数
     */
    int deleteFeeVariableRelByFeeIds(Long[] feeIds);
}
