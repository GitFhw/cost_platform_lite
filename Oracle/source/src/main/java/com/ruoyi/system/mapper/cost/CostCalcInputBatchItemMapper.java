package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostCalcInputBatchItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 正式核算输入批次明细 Mapper
 *
 * @author HwFan
 */
public interface CostCalcInputBatchItemMapper extends BaseMapper<CostCalcInputBatchItem> {
    /**
     * 批量写入导入批次明细。
     */
    int insertBatch(@Param("list") List<CostCalcInputBatchItem> list);
}
