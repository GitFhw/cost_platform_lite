package com.ruoyi.system.service.cost.remote;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostCalcInputBatch;
import com.ruoyi.system.domain.cost.CostCalcInputBatchItem;
import com.ruoyi.system.mapper.cost.CostCalcInputBatchItemMapper;
import com.ruoyi.system.mapper.cost.CostCalcInputBatchMapper;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.Date;
import java.util.Objects;

/**
 * Manages access-profile input batch shells and resume validation.
 */
@Service
public class AccessProfileInputBatchService {
    private final CostCalcInputBatchMapper calcInputBatchMapper;
    private final CostCalcInputBatchItemMapper calcInputBatchItemMapper;

    public AccessProfileInputBatchService(CostCalcInputBatchMapper calcInputBatchMapper,
                                          CostCalcInputBatchItemMapper calcInputBatchItemMapper) {
        this.calcInputBatchMapper = calcInputBatchMapper;
        this.calcInputBatchItemMapper = calcInputBatchItemMapper;
    }

    public CostCalcInputBatch createShell(Long sceneId, Long versionId, String batchNo, String billMonth,
                                          String remark, String sourceType, String operator) {
        Date now = DateUtils.getNowDate();
        CostCalcInputBatch batch = new CostCalcInputBatch();
        batch.setBatchNo(batchNo);
        batch.setSceneId(sceneId);
        batch.setVersionId(versionId);
        batch.setBillMonth(billMonth);
        batch.setSourceType(firstNonBlank(sourceType, "JSON_IMPORT"));
        batch.setBatchStatus(INPUT_BATCH_STATUS_READY);
        batch.setTotalCount(0);
        batch.setValidCount(0);
        batch.setErrorCount(0);
        batch.setRemark(remark);
        batch.setErrorMessage("");
        batch.setCreateBy(operator);
        batch.setCreateTime(now);
        batch.setUpdateBy(operator);
        batch.setUpdateTime(now);
        calcInputBatchMapper.insert(batch);
        return batch;
    }

    public CostCalcInputBatch createShell(Long sceneId, Long versionId, String batchNo, String billMonth,
                                          String remark, String sourceType, Long accessProfileId,
                                          String batchStatus, String operator) {
        CostCalcInputBatch batch = createShell(sceneId, versionId, batchNo, billMonth, remark, sourceType, operator);
        batch.setAccessProfileId(accessProfileId);
        batch.setBatchStatus(firstNonBlank(batchStatus, batch.getBatchStatus()));
        batch.setCheckpointJson("");
        batch.setUpdateBy(operator);
        batch.setUpdateTime(DateUtils.getNowDate());
        calcInputBatchMapper.updateById(batch);
        return batch;
    }

    public CostCalcInputBatch requireResumeBatch(CostAccessProfile profile, Long resumeBatchId, String billMonth) {
        CostCalcInputBatch batch = calcInputBatchMapper.selectById(resumeBatchId);
        if (batch == null) {
            throw new ServiceException("待继续的导入批次不存在，请刷新后重试");
        }
        if (!Objects.equals(batch.getSceneId(), profile.getSceneId())) {
            throw new ServiceException("待继续的导入批次与当前场景不匹配");
        }
        if (profile.getVersionId() != null && !Objects.equals(batch.getVersionId(), profile.getVersionId())) {
            throw new ServiceException("待继续的导入批次与当前版本不匹配");
        }
        if (batch.getAccessProfileId() != null && !Objects.equals(batch.getAccessProfileId(), profile.getProfileId())) {
            throw new ServiceException("待继续的导入批次与当前接入方案不匹配");
        }
        if (StringUtils.isNotEmpty(billMonth) && !Objects.equals(StringUtils.trim(billMonth), batch.getBillMonth())) {
            throw new ServiceException("继续装载时账期必须与原导入批次保持一致");
        }
        return batch;
    }

    public int countItems(Long batchId) {
        return Math.toIntExact(calcInputBatchItemMapper.selectCount(Wrappers.<CostCalcInputBatchItem>lambdaQuery()
                .eq(CostCalcInputBatchItem::getBatchId, batchId)));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }
}
