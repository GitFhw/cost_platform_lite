package com.ruoyi.system.service.cost.remote;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostPublishSnapshot;
import com.ruoyi.system.domain.cost.CostPublishVersion;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.bo.CostAccessProfileBuildBatchBo;
import com.ruoyi.system.domain.cost.bo.CostAccessProfilePreviewFetchBo;
import com.ruoyi.system.mapper.cost.CostAccessProfileMapper;
import com.ruoyi.system.mapper.cost.CostPublishVersionMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.cost.ICostRunService;
import com.ruoyi.system.service.cost.remote.AccessProfileInputMappingService.InputBuildContext;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AccessProfileRunService {
    private static final String VERSION_STATUS_ACTIVE = "ACTIVE";
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int DEFAULT_ACCESS_PREVIEW_RECORD_LIMIT = 20;

    private final CostAccessProfileMapper accessProfileMapper;
    private final CostSceneMapper sceneMapper;
    private final CostPublishVersionMapper publishVersionMapper;
    private final AccessProfileBatchFacade accessProfileBatchFacade;
    private final AccessProfileInputMappingService accessProfileInputMappingService;
    private final ICostRunService runService;

    public AccessProfileRunService(CostAccessProfileMapper accessProfileMapper,
                                   CostSceneMapper sceneMapper,
                                   CostPublishVersionMapper publishVersionMapper,
                                   AccessProfileBatchFacade accessProfileBatchFacade,
                                   AccessProfileInputMappingService accessProfileInputMappingService,
                                   ICostRunService runService) {
        this.accessProfileMapper = accessProfileMapper;
        this.sceneMapper = sceneMapper;
        this.publishVersionMapper = publishVersionMapper;
        this.accessProfileBatchFacade = accessProfileBatchFacade;
        this.accessProfileInputMappingService = accessProfileInputMappingService;
        this.runService = runService;
    }

    public Map<String, Object> previewBuiltInputByProfile(Long profileId, CostAccessProfilePreviewFetchBo bo) {
        CostAccessProfile profile = requireProfile(profileId);
        assertPreviewableProfile(profile);
        InputBuildContext context = buildInputBuildContext(profile);
        String payloadJson = bo == null ? "" : bo.getRequestPayloadJson();
        return accessProfileBatchFacade.preview(profile, context, payloadJson);
    }

    public Map<String, Object> createInputBatchByProfile(Long profileId, CostAccessProfileBuildBatchBo bo) {
        if (bo == null) {
            throw new ServiceException("按接入方案生成导入批次时，请补充账期");
        }
        CostAccessProfile profile = requireProfile(profileId);
        InputBuildContext context = buildInputBuildContext(profile);
        AccessProfileFetchConfig fetchConfig = accessProfileBatchFacade.buildFetchConfig(profile);
        String remark = firstNonBlank(StringUtils.trim(bo.getRemark()), buildProfileInputBatchRemark(profile));
        Map<String, Object> batchResult;
        if (bo.getResumeBatchId() != null) {
            if (!fetchConfig.paged) {
                throw new ServiceException("当前接入方案未启用分页拉取，不支持继续装载导入批次");
            }
            batchResult = accessProfileBatchFacade.resumePaged(profile, context, bo, remark, fetchConfig, resolveOperator(),
                    batchId -> runService.selectInputBatchDetail(batchId, 1, 10), DEFAULT_ACCESS_PREVIEW_RECORD_LIMIT);
        } else if (!fetchConfig.paged) {
            String billMonth = StringUtils.trim(bo.getBillMonth());
            if (StringUtils.isEmpty(billMonth)) {
                throw new ServiceException("按接入方案生成导入批次时，请补充账期");
            }
            validateBillMonth(billMonth);
            batchResult = accessProfileBatchFacade.createNonPaged(profile, context, fetchConfig, billMonth,
                    bo.getRequestPayloadJson(), remark, resolveOperator(), runService::createInputBatch);
        } else {
            String billMonth = StringUtils.trim(bo.getBillMonth());
            if (StringUtils.isEmpty(billMonth)) {
                throw new ServiceException("按接入方案生成导入批次时，请补充账期");
            }
            validateBillMonth(billMonth);
            AccessProfileVersionMeta versionMeta = requireFormalVersion(profile.getSceneId(), profile.getVersionId());
            batchResult = accessProfileBatchFacade.createPaged(profile, context, fetchConfig,
                    versionMeta.sceneId, versionMeta.versionId, buildRunNo("INPUT"), billMonth, bo.getRequestPayloadJson(),
                    remark, resolveOperator(), batchId -> runService.selectInputBatchDetail(batchId, 1, 10),
                    DEFAULT_ACCESS_PREVIEW_RECORD_LIMIT);
        }
        return accessProfileBatchFacade.complete(batchResult, accessProfileBatchFacade.summarize(profile));
    }

    private CostAccessProfile requireProfile(Long profileId) {
        CostAccessProfile profile = accessProfileMapper.selectAccessProfileById(profileId);
        if (profile == null) {
            throw new ServiceException("接入方案不存在，请刷新后重试");
        }
        populateProfileFeeScope(profile);
        return profile;
    }

    private void assertPreviewableProfile(CostAccessProfile profile) {
        if (!STATUS_ENABLED.equals(profile.getStatus())) {
            throw new ServiceException("当前接入方案已停用，无法直接拉取预演");
        }
        if (!ACCESS_SOURCE_TYPE_HTTP_API.equalsIgnoreCase(profile.getSourceType())) {
            throw new ServiceException("当前接入方案不是 HTTP 接口类型，不能执行直连预演");
        }
        if (StringUtils.isEmpty(profile.getEndpointUrl())) {
            throw new ServiceException("当前接入方案未配置接口地址，无法执行直连预演");
        }
    }

    private InputBuildContext buildInputBuildContext(CostAccessProfile profile) {
        List<Long> feeIds = resolveProfileFeeIds(profile);
        Map<String, Object> template = runService.buildFeeInputTemplate(profile.getSceneId(), profile.getVersionId(),
                feeIds, profile.getFeeId(), profile.getFeeCode(), profile.getTaskType());
        return accessProfileInputMappingService.buildContext(template, profile.getMappingJson());
    }

    private AccessProfileVersionMeta requireFormalVersion(Long sceneId, Long versionId) {
        CostScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new ServiceException("场景不存在，请刷新后重试");
        }
        Long targetVersionId = versionId == null ? scene.getActiveVersionId() : versionId;
        if (targetVersionId == null) {
            throw new ServiceException("当前场景尚未设置生效版本，无法执行运行链");
        }
        CostPublishVersion version = publishVersionMapper.selectPublishVersionDetail(targetVersionId);
        if (version == null) {
            throw new ServiceException("发布版本不存在，请刷新后重试");
        }
        if (versionId == null && !VERSION_STATUS_ACTIVE.equals(version.getVersionStatus())) {
            throw new ServiceException("正式核算默认只能按当前生效版本执行");
        }
        List<CostPublishSnapshot> snapshots = publishVersionMapper.selectSnapshotList(targetVersionId, null);
        if (snapshots == null || snapshots.isEmpty()) {
            throw new ServiceException("发布快照为空，无法执行试算或正式核算");
        }
        return new AccessProfileVersionMeta(scene.getSceneId(), targetVersionId);
    }

    private void validateBillMonth(String billMonth) {
        if (!billMonth.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException("账期格式必须为 yyyy-MM");
        }
    }

    private String buildProfileInputBatchRemark(CostAccessProfile profile) {
        return profile.getProfileCode() + " 直连业务接口生成导入批次";
    }

    private String resolveOperator() {
        try {
            return firstNonBlank(SecurityUtils.getUsername(), "system");
        } catch (Exception ignored) {
            return "system";
        }
    }

    private String buildRunNo(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(first) ? first : second;
    }

    private void populateProfileFeeScope(CostAccessProfile profile) {
        List<Long> feeIds = resolveProfileFeeIds(profile);
        profile.setFeeIds(new ArrayList<>(feeIds));
        if (feeIds.isEmpty()) {
            profile.setFeeScopeType("ALL");
            profile.setFeeId(null);
            return;
        }
        if (feeIds.size() == 1) {
            profile.setFeeScopeType("SINGLE");
            profile.setFeeId(feeIds.get(0));
            return;
        }
        profile.setFeeScopeType("MULTI");
        profile.setFeeId(null);
    }

    private List<Long> resolveProfileFeeIds(CostAccessProfile profile) {
        if (profile == null) {
            return Collections.emptyList();
        }
        Set<Long> feeIds = new LinkedHashSet<>();
        if (profile.getFeeIds() != null) {
            for (Long feeId : profile.getFeeIds()) {
                if (feeId != null) {
                    feeIds.add(feeId);
                }
            }
        }
        if (feeIds.isEmpty() && StringUtils.isNotEmpty(profile.getFeeIdsJson())) {
            try {
                String text = profile.getFeeIdsJson().trim();
                if (text.startsWith("[") && text.endsWith("]")) {
                    String body = text.substring(1, text.length() - 1).trim();
                    if (StringUtils.isNotEmpty(body)) {
                        for (String token : body.split(",")) {
                            String candidate = token.trim();
                            if (StringUtils.isNotEmpty(candidate)) {
                                feeIds.add(Long.valueOf(candidate));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new ServiceException("接入方案费用范围配置解析失败");
            }
        }
        if (feeIds.isEmpty() && profile.getFeeId() != null) {
            feeIds.add(profile.getFeeId());
        }
        return new ArrayList<>(feeIds);
    }

    private static class AccessProfileVersionMeta {
        private final Long sceneId;
        private final Long versionId;

        private AccessProfileVersionMeta(Long sceneId, Long versionId) {
            this.sceneId = sceneId;
            this.versionId = versionId;
        }
    }
}
