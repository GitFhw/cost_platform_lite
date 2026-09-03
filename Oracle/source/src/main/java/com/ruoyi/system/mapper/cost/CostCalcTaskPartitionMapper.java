package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostCalcTaskPartition;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 正式核算任务分片 Mapper
 *
 * @author HwFan
 */
public interface CostCalcTaskPartitionMapper extends BaseMapper<CostCalcTaskPartition> {
    /**
     * 批量写入任务分片。
     */
    int insertBatch(@Param("list") List<CostCalcTaskPartition> list);

    BigDecimal sumAmountByTaskId(@Param("taskId") Long taskId);
}
