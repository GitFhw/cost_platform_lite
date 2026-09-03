package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostCalcTaskDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 核算任务明细 Mapper
 *
 * @author HwFan
 */
public interface CostCalcTaskDetailMapper extends BaseMapper<CostCalcTaskDetail> {
    /**
     * 批量写入任务明细。
     */
    int insertBatch(@Param("list") List<CostCalcTaskDetail> list);

    /**
     * 批量回写任务明细执行结果。
     */
    int updateBatchResult(@Param("list") List<CostCalcTaskDetail> list);

    /**
     * 汇总失败原因 TopN。
     */
    List<Map<String, Object>> selectTopErrors(@Param("taskId") Long taskId, @Param("limit") int limit);
}
