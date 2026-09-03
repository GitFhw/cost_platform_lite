package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostAccessProfile;
import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.domain.cost.CostPublishVersion;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.mapper.cost.CostAccessProfileMapper;
import com.ruoyi.system.mapper.cost.CostFeeMapper;
import com.ruoyi.system.mapper.cost.CostPublishVersionMapper;
import com.ruoyi.system.mapper.cost.CostSceneMapper;
import com.ruoyi.system.service.cost.ICostAccessProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CostAccessProfileServiceImpl implements ICostAccessProfileService {
    private static final String DEFAULT_SOURCE_TYPE = "RAW_JSON";
    private static final String DEFAULT_TASK_TYPE = TASK_TYPE_FORMAL_BATCH;
    private static final String DEFAULT_REQUEST_METHOD = "GET";
    private static final String DEFAULT_AUTH_TYPE = "NONE";
    private static final String DEFAULT_STATUS = STATUS_ENABLED;
    private static final String DEFAULT_FEE_SCOPE_TYPE = "ALL";
    private static final String FEE_SCOPE_SINGLE = "SINGLE";
    private static final String FEE_SCOPE_MULTI = "MULTI";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CostAccessProfileMapper accessProfileMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostFeeMapper feeMapper;

    @Autowired
    private CostPublishVersionMapper publishVersionMapper;

    @Override
    public List<CostAccessProfile> selectAccessProfileList(CostAccessProfile query) {
        return enrichProfiles(accessProfileMapper.selectAccessProfileList(query));
    }

    @Override
    public List<CostAccessProfile> selectAccessProfileOptions(CostAccessProfile query) {
        CostAccessProfile optionQuery = query == null ? new CostAccessProfile() : query;
        if (StringUtils.isEmpty(optionQuery.getStatus())) {
            optionQuery.setStatus(DEFAULT_STATUS);
        }
        return enrichProfiles(accessProfileMapper.selectAccessProfileList(optionQuery));
    }

    @Override
    public CostAccessProfile selectAccessProfileById(Long profileId) {
        return enrichProfile(accessProfileMapper.selectAccessProfileById(profileId));
    }

    @Override
    public boolean checkProfileCodeUnique(CostAccessProfile profile) {
        Long profileId = profile.getProfileId() == null ? -1L : profile.getProfileId();
        Long count = accessProfileMapper.selectCount(Wrappers.<CostAccessProfile>lambdaQuery()
                .eq(CostAccessProfile::getSceneId, profile.getSceneId())
                .eq(CostAccessProfile::getProfileCode, profile.getProfileCode())
                .ne(profileId != -1L, CostAccessProfile::getProfileId, profileId));
        return count == null || count == 0 ? UserConstants.UNIQUE : UserConstants.NOT_UNIQUE;
    }

    @Override
    public int insertAccessProfile(CostAccessProfile profile) {
        normalizeProfile(profile);
        validateProfile(profile);
        fillDefaultFields(profile, true);
        return accessProfileMapper.insert(profile);
    }

    @Override
    public int updateAccessProfile(CostAccessProfile profile) {
        if (profile == null || profile.getProfileId() == null) {
            throw new ServiceException("请选择需要修改的接入方案");
        }
        CostAccessProfile existing = accessProfileMapper.selectById(profile.getProfileId());
        if (existing == null) {
            throw new ServiceException("接入方案不存在，请刷新后重试");
        }
        normalizeProfile(profile);
        validateProfile(profile);
        fillDefaultFields(profile, false);
        profile.setCreateBy(existing.getCreateBy());
        profile.setCreateTime(existing.getCreateTime());
        return accessProfileMapper.updateById(profile);
    }

    @Override
    public int deleteAccessProfileByIds(Long[] profileIds) {
        if (profileIds == null || profileIds.length == 0) {
            return 0;
        }
        return accessProfileMapper.deleteBatchIds(Arrays.asList(profileIds));
    }

    private void validateProfile(CostAccessProfile profile) {
        if (profile == null) {
            throw new ServiceException("接入方案不能为空");
        }
        CostScene scene = sceneMapper.selectById(profile.getSceneId());
        if (scene == null) {
            throw new ServiceException("所属场景不存在，请刷新后重试");
        }
        List<Long> selectedFeeIds = resolveProfileFeeIds(profile);
        for (Long feeId : selectedFeeIds) {
            CostFeeItem fee = feeMapper.selectById(feeId);
            if (fee == null || !profile.getSceneId().equals(fee.getSceneId())) {
                throw new ServiceException("目标费用不存在或不属于当前场景");
            }
        }
        if (profile.getVersionId() != null) {
            CostPublishVersion version = publishVersionMapper.selectById(profile.getVersionId());
            if (version == null || !profile.getSceneId().equals(version.getSceneId())) {
                throw new ServiceException("绑定版本不存在或不属于当前场景");
            }
        }
        if (!checkProfileCodeUnique(profile)) {
            throw new ServiceException("同场景下接入方案编码已存在");
        }
    }

    private void normalizeProfile(CostAccessProfile profile) {
        if (profile == null) {
            return;
        }
        profile.setProfileCode(StringUtils.trim(profile.getProfileCode()));
        profile.setProfileName(StringUtils.trim(profile.getProfileName()));
        profile.setSourceType(StringUtils.upperCase(StringUtils.trim(profile.getSourceType())));
        profile.setTaskType(StringUtils.trim(profile.getTaskType()));
        profile.setFeeScopeType(StringUtils.upperCase(StringUtils.trim(profile.getFeeScopeType())));
        profile.setRequestMethod(StringUtils.upperCase(StringUtils.trim(profile.getRequestMethod())));
        profile.setEndpointUrl(StringUtils.trim(profile.getEndpointUrl()));
        profile.setAuthType(StringUtils.upperCase(StringUtils.trim(profile.getAuthType())));
        profile.setAuthConfigJson(trimJson(profile.getAuthConfigJson()));
        profile.setFetchConfigJson(trimJson(profile.getFetchConfigJson()));
        profile.setMappingJson(trimJson(profile.getMappingJson()));
        profile.setSamplePayloadJson(trimJson(profile.getSamplePayloadJson()));
        profile.setSampleInputJson(trimJson(profile.getSampleInputJson()));
        profile.setStatus(StringUtils.trim(profile.getStatus()));
        profile.setRemark(StringUtils.trim(profile.getRemark()));
        normalizeFeeScope(profile);
    }

    private void fillDefaultFields(CostAccessProfile profile, boolean isInsert) {
        if (isInsert && profile.getCreateTime() == null) {
            profile.setCreateTime(DateUtils.getNowDate());
        }
        if (profile.getUpdateTime() == null) {
            profile.setUpdateTime(DateUtils.getNowDate());
        }
        if (StringUtils.isEmpty(profile.getSourceType())) {
            profile.setSourceType(DEFAULT_SOURCE_TYPE);
        }
        if (StringUtils.isEmpty(profile.getTaskType())) {
            profile.setTaskType(DEFAULT_TASK_TYPE);
        }
        if (StringUtils.isEmpty(profile.getRequestMethod())) {
            profile.setRequestMethod(DEFAULT_REQUEST_METHOD);
        }
        if (StringUtils.isEmpty(profile.getAuthType())) {
            profile.setAuthType(DEFAULT_AUTH_TYPE);
        }
        if (StringUtils.isEmpty(profile.getStatus())) {
            profile.setStatus(DEFAULT_STATUS);
        }
        if (StringUtils.isEmpty(profile.getFeeScopeType())) {
            profile.setFeeScopeType(DEFAULT_FEE_SCOPE_TYPE);
        }
        if (profile.getSortNo() == null) {
            profile.setSortNo(0);
        }
        if (isInsert) {
            profile.setUpdateBy(profile.getCreateBy());
        }
    }

    private String trimJson(String value) {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isEmpty(trimmed) ? "" : trimmed;
    }

    private List<CostAccessProfile> enrichProfiles(List<CostAccessProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return profiles;
        }
        profiles.forEach(this::enrichProfile);
        return profiles;
    }

    private CostAccessProfile enrichProfile(CostAccessProfile profile) {
        if (profile == null) {
            return null;
        }
        List<Long> feeIds = resolveProfileFeeIds(profile);
        profile.setFeeIds(new ArrayList<>(feeIds));
        if (feeIds.isEmpty()) {
            profile.setFeeScopeType(DEFAULT_FEE_SCOPE_TYPE);
            profile.setFeeId(null);
            return profile;
        }
        if (feeIds.size() == 1) {
            profile.setFeeScopeType(FEE_SCOPE_SINGLE);
            profile.setFeeId(feeIds.get(0));
            return profile;
        }
        profile.setFeeScopeType(FEE_SCOPE_MULTI);
        profile.setFeeId(null);
        return profile;
    }

    private void normalizeFeeScope(CostAccessProfile profile) {
        List<Long> feeIds = resolveProfileFeeIds(profile);
        profile.setFeeIds(new ArrayList<>(feeIds));
        if (feeIds.isEmpty()) {
            profile.setFeeScopeType(DEFAULT_FEE_SCOPE_TYPE);
            profile.setFeeId(null);
            profile.setFeeIdsJson("");
            return;
        }
        if (feeIds.size() == 1) {
            profile.setFeeScopeType(FEE_SCOPE_SINGLE);
            profile.setFeeId(feeIds.get(0));
            profile.setFeeIdsJson("");
            return;
        }
        profile.setFeeScopeType(FEE_SCOPE_MULTI);
        profile.setFeeId(null);
        profile.setFeeIdsJson(writeFeeIdsJson(feeIds));
    }

    private List<Long> resolveProfileFeeIds(CostAccessProfile profile) {
        if (profile == null) {
            return Collections.emptyList();
        }
        Set<Long> orderedIds = new LinkedHashSet<>();
        if (profile.getFeeIds() != null) {
            for (Long feeId : profile.getFeeIds()) {
                if (feeId != null) {
                    orderedIds.add(feeId);
                }
            }
        }
        if (orderedIds.isEmpty() && StringUtils.isNotEmpty(profile.getFeeIdsJson())) {
            try {
                List<Long> jsonFeeIds = objectMapper.readValue(profile.getFeeIdsJson(), new TypeReference<List<Long>>() {
                });
                if (jsonFeeIds != null) {
                    for (Long feeId : jsonFeeIds) {
                        if (feeId != null) {
                            orderedIds.add(feeId);
                        }
                    }
                }
            } catch (Exception e) {
                throw new ServiceException("接入方案费用范围配置解析失败");
            }
        }
        if (orderedIds.isEmpty() && profile.getFeeId() != null) {
            orderedIds.add(profile.getFeeId());
        }
        return new ArrayList<>(orderedIds);
    }

    private String writeFeeIdsJson(List<Long> feeIds) {
        try {
            return objectMapper.writeValueAsString(feeIds);
        } catch (Exception e) {
            throw new ServiceException("接入方案费用范围保存失败");
        }
    }
}
