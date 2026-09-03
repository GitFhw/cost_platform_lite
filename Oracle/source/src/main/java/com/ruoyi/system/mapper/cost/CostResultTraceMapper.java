package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostResultTrace;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 结果追溯 Mapper
 *
 * @author HwFan
 */
public interface CostResultTraceMapper extends BaseMapper<CostResultTrace> {
    /**
     * 批量写入结果追溯。
     */
    int insertBatch(@Param("list") List<CostResultTrace> list);
}
