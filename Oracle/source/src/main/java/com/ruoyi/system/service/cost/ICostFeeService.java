package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.domain.vo.CostFeeGovernanceCheckVo;

import java.util.List;
import java.util.Map;

/**
 * 费用中心服务接口
 *
 * @author HwFan
 */
public interface ICostFeeService {
    /**
     * 查询费用列表
     *
     * @param feeItem 查询对象
     *
     * @return 费用集合
     */
    List<CostFeeItem> selectFeeList(CostFeeItem feeItem);

    /**
     * 查询费用详情
     *
     * @param feeId 费用主键
     *
     * @return 费用对象
     */
    CostFeeItem selectFeeById(Long feeId);

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
     * 校验费用编码是否唯一
     *
     * @param feeItem 费用对象
     *
     * @return 结果
     */
    boolean checkFeeCodeUnique(CostFeeItem feeItem);

    /**
     * 新增费用
     *
     * @param feeItem 费用对象
     *
     * @return 结果
     */
    int insertFee(CostFeeItem feeItem);

    /**
     * 修改费用
     *
     * @param feeItem 费用对象
     *
     * @return 结果
     */
    int updateFee(CostFeeItem feeItem);

    /**
     * 批量停用费用
     *
     * @param feeIds 费用主键数组
     *
     * @return 结果
     */
    int disableFeeByIds(Long[] feeIds);

    /**
     * 批量删除费用
     *
     * @param feeIds 费用主键数组
     *
     * @return 结果
     */
    int deleteFeeByIds(Long[] feeIds);
}
