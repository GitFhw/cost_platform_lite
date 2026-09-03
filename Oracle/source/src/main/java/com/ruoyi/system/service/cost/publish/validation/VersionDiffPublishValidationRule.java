package com.ruoyi.system.service.cost.publish.validation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.mapper.cost.CostSimulationRecordMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class VersionDiffPublishValidationRule implements PublishValidationRule {
    private static final String SIMULATION_STATUS_SUCCESS = "SUCCESS";

    private final CostSimulationRecordMapper simulationRecordMapper;

    public VersionDiffPublishValidationRule(CostSimulationRecordMapper simulationRecordMapper) {
        this.simulationRecordMapper = simulationRecordMapper;
    }

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        if (context.getActiveVersion() == null) {
            items.add(PublishValidationSupport.checkItem("WARN", "FIRST_RELEASE", "首发提醒", "当前场景还没有生效版本，本次发布后建议尽快设为生效。"));
            appendFirstReleaseSimulationCheck(context, items);
            return;
        }
        if (StringUtils.equals(context.getActiveVersion().getSnapshotHash(), context.getDraftSnapshotHash())) {
            items.add(PublishValidationSupport.checkItem("WARN", "SNAPSHOT_NO_CHANGE", "版本差异提醒",
                    String.format("当前草稿与生效版本%1$s的快照哈希一致，发布后可能不会产生业务差异。", context.getActiveVersion().getVersionNo())));
            return;
        }
        items.add(PublishValidationSupport.checkItem("PASS", "SNAPSHOT_CHANGED", "版本差异提醒",
                String.format(Locale.ROOT, "当前草稿相较生效版本%1$s识别到%2$d个受影响费用。",
                        context.getActiveVersion().getVersionNo(), context.getImpactedFeeCount())));
    }

    private void appendFirstReleaseSimulationCheck(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        long successSimulationCount = simulationRecordMapper.selectCount(Wrappers.lambdaQuery(CostSimulationRecord.class)
                .eq(CostSimulationRecord::getSceneId, context.getSceneId())
                .eq(CostSimulationRecord::getStatus, SIMULATION_STATUS_SUCCESS));
        if (successSimulationCount > 0) {
            items.add(PublishValidationSupport.checkItem("PASS", "FIRST_RELEASE_SIMULATION_READY", "试算提醒",
                    String.format(Locale.ROOT, "当前场景已有%1$d条成功试算记录，可作为首次发布前的业务核对依据。", successSimulationCount)));
            return;
        }
        items.add(PublishValidationSupport.checkItem("WARN", "FIRST_RELEASE_NO_SIMULATION", "试算提醒",
                "当前场景为首次发布，且尚无成功试算记录，建议先完成至少1次试算核对再发布。"));
    }
}
