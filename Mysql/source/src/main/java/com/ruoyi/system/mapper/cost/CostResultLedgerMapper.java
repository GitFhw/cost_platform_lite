package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostResultLedger;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 结果台账 Mapper
 *
 * @author HwFan
 */
public interface CostResultLedgerMapper extends BaseMapper<CostResultLedger> {
    /**
     * 批量写入结果台账。
     */
    int insertBatch(@Param("list") List<CostResultLedger> list);

    Long countBySceneAndBillMonth(@Param("sceneId") Long sceneId, @Param("billMonth") String billMonth);

    BigDecimal sumAmountBySceneAndBillMonth(@Param("sceneId") Long sceneId, @Param("billMonth") String billMonth);

    BigDecimal sumAmountByTaskId(@Param("taskId") Long taskId);

    Map<String, Object> selectStats(@Param("query") CostResultLedger query,
                                    @Param("requestTaskIds") List<Long> requestTaskIds);

    List<Map<String, Object>> selectFeeDistribution(@Param("query") CostResultLedger query,
                                                    @Param("requestTaskIds") List<Long> requestTaskIds);

    List<Map<String, Object>> selectObjectDistribution(@Param("query") CostResultLedger query,
                                                       @Param("requestTaskIds") List<Long> requestTaskIds);
}
