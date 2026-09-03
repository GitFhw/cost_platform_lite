package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.*;
import com.ruoyi.system.domain.cost.bo.CostPublishCreateBo;
import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import com.ruoyi.system.mapper.cost.*;
import com.ruoyi.system.service.cost.ICostPublishService;
import com.ruoyi.system.service.cost.publish.PublishSnapshotBundle;
import com.ruoyi.system.service.cost.publish.PublishSnapshotViewService;
import com.ruoyi.system.service.cost.publish.validation.PublishJsonSupport;
import com.ruoyi.system.service.cost.publish.validation.PublishValidationChain;
import com.ruoyi.system.service.cost.publish.validation.PublishValidationContext;
import com.ruoyi.system.service.cost.publish.validation.PublishValidationSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 发布中心服务实现
 *
 * @author HwFan
 */
@Service
public class CostPublishServiceImpl implements ICostPublishService {
    private static final String VERSION_STATUS_PUBLISHED = "PUBLISHED";
    private static final String VERSION_STATUS_ACTIVE = "ACTIVE";
    private static final String VERSION_STATUS_ROLLED_BACK = "ROLLED_BACK";
    private static final String SNAPSHOT_SCENE = "SCENE";
    private static final String SNAPSHOT_FEE = "FEE";
    private static final String SNAPSHOT_VARIABLE = "VARIABLE";
    private static final String SNAPSHOT_FORMULA = "FORMULA";
    private static final String SNAPSHOT_RULE = "RULE";
    private static final String SNAPSHOT_RULE_CONDITION = "RULE_CONDITION";
    private static final String SNAPSHOT_RULE_TIER = "RULE_TIER";
    private static final DecimalFormat VERSION_SEQ_FORMAT = new DecimalFormat("000");

    @Autowired
    private CostPublishVersionMapper publishVersionMapper;

    @Autowired
    private CostPublishSnapshotMapper publishSnapshotMapper;

    @Autowired
    private CostSceneMapper sceneMapper;

    @Autowired
    private CostFeeMapper feeMapper;

    @Autowired
    private CostVariableMapper variableMapper;

    @Autowired
    private CostFormulaMapper formulaMapper;

    @Autowired
    private CostRuleMapper ruleMapper;


    @Autowired
    private CostDistributedLockSupport distributedLockSupport;

    @Autowired
    private PublishValidationChain publishValidationChain;

    @Autowired
    private PublishSnapshotViewService snapshotViewService;

    @Override
    public Map<String, Object> selectPublishStats(CostPublishVersion query) {
        Map<String, Object> stats = publishVersionMapper.selectPublishStats(query);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneCount", getLongValue(stats, "sceneCount"));
        result.put("versionCount", getLongValue(stats, "versionCount"));
        result.put("activeVersionCount", getLongValue(stats, "activeVersionCount"));
        result.put("rolledBackVersionCount", getLongValue(stats, "rolledBackVersionCount"));
        result.put("latestWarningCount", 0);
        result.put("latestBlockingCount", 0);
        result.put("latestCheckResult", "UNAVAILABLE");
        result.put("latestCheckLabel", "暂无校验");
        if (query != null && query.getSceneId() != null) {
            CostPublishVersion activeVersion = publishVersionMapper.selectActiveVersionByScene(query.getSceneId());
            CostPublishVersion latestVersion = publishVersionMapper.selectLatestVersionByScene(query.getSceneId());
            result.put("activeVersionId", activeVersion == null ? null : activeVersion.getVersionId());
            result.put("activeVersionNo", activeVersion == null ? null : activeVersion.getVersionNo());
            result.put("latestVersionId", latestVersion == null ? null : latestVersion.getVersionId());
            result.put("latestVersionNo", latestVersion == null ? null : latestVersion.getVersionNo());
            Map<String, Object> latestValidation = latestVersion == null ? Collections.emptyMap()
                    : PublishJsonSupport.parseJsonMap(latestVersion.getValidationResultJson());
            long warningCount = getLongValue(latestValidation, "warningCount");
            long blockingCount = getLongValue(latestValidation, "blockingCount");
            result.put("latestWarningCount", warningCount);
            result.put("latestBlockingCount", blockingCount);
            result.put("latestCheckResult", resolveCheckResult(blockingCount, warningCount, latestVersion));
            result.put("latestCheckLabel", resolveCheckLabel(blockingCount, warningCount, latestVersion));
        }
        return result;
    }

    @Override
    public Map<String, Object> selectPublishPrecheck(Long sceneId) {
        PublishSnapshotBundle draftBundle = buildDraftSnapshotBundle(sceneId);
        CostPublishVersion activeVersion = publishVersionMapper.selectActiveVersionByScene(sceneId);
        PublishSnapshotBundle activeBundle = snapshotViewService.normalizeBundle(activeVersion == null ? null : loadSnapshotBundle(activeVersion.getVersionId()));
        CostPublishVersion latestVersion = publishVersionMapper.selectLatestVersionByScene(sceneId);

        boolean sameAsActiveSnapshot = activeVersion != null && snapshotViewService.isSnapshotEquivalent(activeBundle, draftBundle);
        List<Map<String, Object>> impactedFees = sameAsActiveSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildFeeDiffSummary(activeBundle, draftBundle, null);
        List<CostPublishCheckItemVo> items = new ArrayList<>();
        publishValidationChain.validate(buildPublishValidationContext(sceneId, draftBundle, activeVersion, impactedFees.size()), items);
        long blockingCount = items.stream().filter(item -> "BLOCK".equals(item.getLevel())).count();
        long warningCount = items.stream().filter(item -> "WARN".equals(item.getLevel())).count();
        List<CostPublishCheckItemVo> blockingItems = items.stream()
                .filter(item -> "BLOCK".equals(item.getLevel()))
                .collect(Collectors.toList());
        List<CostPublishCheckItemVo> warningItems = items.stream()
                .filter(item -> "WARN".equals(item.getLevel()))
                .collect(Collectors.toList());

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("sceneId", draftBundle.sceneId);
        result.put("sceneCode", draftBundle.sceneCode);
        result.put("sceneName", draftBundle.sceneName);
        result.put("publishedVersionCount", publishVersionMapper.selectCount(Wrappers.<CostPublishVersion>lambdaQuery()
                .eq(CostPublishVersion::getSceneId, sceneId)));
        result.put("activeVersionId", activeVersion == null ? null : activeVersion.getVersionId());
        result.put("activeVersionNo", activeVersion == null ? null : activeVersion.getVersionNo());
        result.put("latestVersionId", latestVersion == null ? null : latestVersion.getVersionId());
        result.put("latestVersionNo", latestVersion == null ? null : latestVersion.getVersionNo());
        result.put("diffPreview", buildDraftDiffPreview(latestVersion, draftBundle));
        result.put("draftSnapshotHash", draftBundle.snapshotHash);
        result.put("activeSnapshotHash", activeVersion == null ? null : activeVersion.getSnapshotHash());
        result.put("publishable", blockingCount <= 0);
        result.put("blockingCount", blockingCount);
        result.put("warningCount", warningCount);
        result.put("feeCount", draftBundle.feesByCode.size());
        result.put("variableCount", draftBundle.variablesByCode.size());
        result.put("ruleCount", draftBundle.rulesByCode.size());
        result.put("conditionCount", draftBundle.ruleConditionsByRuleCode.values().stream().mapToInt(List::size).sum());
        result.put("tierCount", draftBundle.ruleTiersByRuleCode.values().stream().mapToInt(List::size).sum());
        result.put("items", items);
        result.put("blockingItems", blockingItems);
        result.put("warningItems", warningItems);
        result.put("blockingSummary", buildCheckSummary(blockingItems));
        result.put("warningSummary", buildCheckSummary(warningItems));
        result.put("impactedFeeCount", impactedFees.size());
        result.put("impactedFees", impactedFees);
        result.put("suggestActivateNow", activeVersion == null);
        return result;
    }

    private String buildCheckSummary(List<CostPublishCheckItemVo> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> item.getTitle() + "：" + item.getMessage())
                .collect(Collectors.joining("；"));
    }

    private Map<String, Object> buildDraftDiffPreview(CostPublishVersion previousVersion, PublishSnapshotBundle draftBundle) {
        PublishSnapshotBundle previousBundle = snapshotViewService.normalizeBundle(previousVersion == null ? null : loadSnapshotBundle(previousVersion.getVersionId()));
        boolean sameSnapshot = previousVersion != null && snapshotViewService.isSnapshotEquivalent(previousBundle, draftBundle);
        List<Map<String, Object>> feeDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildObjectDiffSummary(previousBundle.feesByCode, draftBundle.feesByCode,
                "feeCode", "feeName", "feeCode", "feeName");
        List<Map<String, Object>> variableDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildObjectDiffSummary(previousBundle.variablesByCode, draftBundle.variablesByCode,
                "variableCode", "variableName", "variableCode", "variableName");
        List<Map<String, Object>> ruleDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildRuleDiffSummary(previousBundle, draftBundle, null);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        putChangeCounts(summary, "fee", feeDiffs);
        putChangeCounts(summary, "variable", variableDiffs);
        putChangeCounts(summary, "rule", ruleDiffs);
        summary.put("totalChangeCount", feeDiffs.size() + variableDiffs.size() + ruleDiffs.size());

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("previousVersionId", previousVersion == null ? null : previousVersion.getVersionId());
        result.put("previousVersionNo", previousVersion == null ? null : previousVersion.getVersionNo());
        result.put("draftSnapshotHash", draftBundle.snapshotHash);
        result.put("previousSnapshotHash", previousVersion == null ? null : previousVersion.getSnapshotHash());
        result.put("summary", summary);
        result.put("feeDiffs", feeDiffs);
        result.put("variableDiffs", variableDiffs);
        result.put("ruleDiffs", ruleDiffs);
        return result;
    }

    private void putChangeCounts(Map<String, Object> summary, String prefix, List<Map<String, Object>> diffs) {
        summary.put(prefix + "ChangeCount", diffs.size());
        summary.put("added" + capitalize(prefix) + "Count", countChangeType(diffs, "ADDED"));
        summary.put("removed" + capitalize(prefix) + "Count", countChangeType(diffs, "REMOVED"));
        summary.put("changed" + capitalize(prefix) + "Count", countChangeType(diffs, "CHANGED"));
    }

    private long countChangeType(List<Map<String, Object>> diffs, String changeType) {
        return diffs.stream().filter(item -> changeType.equals(item.get("changeType"))).count();
    }

    private String capitalize(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private PublishValidationContext buildPublishValidationContext(Long sceneId,
                                                                   PublishSnapshotBundle draftBundle,
                                                                   CostPublishVersion activeVersion,
                                                                   int impactedFeeCount) {
        PublishValidationContext context = new PublishValidationContext();
        context.setSceneId(sceneId);
        context.setSceneSnapshot(draftBundle.sceneSnapshot);
        context.setFeesByCode(draftBundle.feesByCode);
        context.setVariablesByCode(draftBundle.variablesByCode);
        context.setFormulasByCode(draftBundle.formulasByCode);
        context.setRulesByCode(draftBundle.rulesByCode);
        context.setRuleConditionsByRuleCode(draftBundle.ruleConditionsByRuleCode);
        context.setDraftSnapshotHash(draftBundle.snapshotHash);
        context.setActiveVersion(activeVersion);
        context.setImpactedFeeCount(impactedFeeCount);
        return context;
    }

    @Override
    public List<CostPublishVersion> selectPublishVersionList(CostPublishVersion query) {
        return publishVersionMapper.selectPublishVersionList(query);
    }

    @Override
    public Map<String, Object> selectPublishVersionDetail(Long versionId, String feeCode) {
        CostPublishVersion version = requireVersion(versionId);
        PublishSnapshotBundle currentBundle = snapshotViewService.normalizeBundle(loadSnapshotBundle(versionId));
        CostPublishVersion previousVersion = publishVersionMapper.selectPreviousVersion(version.getSceneId(), versionId);
        PublishSnapshotBundle previousBundle = snapshotViewService.normalizeBundle(previousVersion == null ? null : loadSnapshotBundle(previousVersion.getVersionId()));
        boolean sameAsPreviousSnapshot = previousVersion != null && snapshotViewService.isSnapshotEquivalent(previousBundle, currentBundle);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("version", version);
        result.put("previousVersionId", previousVersion == null ? null : previousVersion.getVersionId());
        result.put("previousVersionNo", previousVersion == null ? null : previousVersion.getVersionNo());
        result.put("validationResult", PublishJsonSupport.parseJsonMap(version.getValidationResultJson()));
        result.put("impactedFees", sameAsPreviousSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildFeeDiffSummary(previousBundle, currentBundle, feeCode));
        result.put("snapshotCounts", snapshotViewService.buildSnapshotCounts(currentBundle, feeCode));
        result.put("snapshotGroups", snapshotViewService.buildSnapshotGroups(currentBundle, feeCode));
        return result;
    }

    @Override
    public Map<String, Object> selectPublishDiff(Long fromVersionId, Long toVersionId, String feeCode) {
        CostPublishVersion fromVersion = requireVersion(fromVersionId);
        CostPublishVersion toVersion = requireVersion(toVersionId);
        if (!Objects.equals(fromVersion.getSceneId(), toVersion.getSceneId())) {
            throw new ServiceException("版本差异只能在同一场景下比较");
        }
        PublishSnapshotBundle fromBundle = snapshotViewService.normalizeBundle(loadSnapshotBundle(fromVersionId));
        PublishSnapshotBundle toBundle = snapshotViewService.normalizeBundle(loadSnapshotBundle(toVersionId));
        boolean sameSnapshot = snapshotViewService.isSnapshotEquivalent(fromBundle, toBundle);
        List<Map<String, Object>> feeDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildFeeDiffSummary(fromBundle, toBundle, feeCode);
        List<Map<String, Object>> ruleDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildRuleDiffSummary(fromBundle, toBundle, feeCode);
        List<Map<String, Object>> sceneDiffs = sameSnapshot
                ? Collections.emptyList()
                : snapshotViewService.buildSceneDiffSummary(fromBundle.sceneSnapshot, toBundle.sceneSnapshot);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("sceneChangeCount", sceneDiffs.size());
        summary.put("feeChangeCount", feeDiffs.size());
        summary.put("addedFeeCount", feeDiffs.stream().filter(item -> "ADDED".equals(item.get("changeType"))).count());
        summary.put("removedFeeCount", feeDiffs.stream().filter(item -> "REMOVED".equals(item.get("changeType"))).count());
        summary.put("changedFeeCount", feeDiffs.stream().filter(item -> "CHANGED".equals(item.get("changeType"))).count());
        summary.put("ruleChangeCount", ruleDiffs.size());

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("fromVersion", fromVersion);
        result.put("toVersion", toVersion);
        result.put("summary", summary);
        result.put("fromScene", fromBundle.sceneSnapshot);
        result.put("toScene", toBundle.sceneSnapshot);
        result.put("sceneDiffs", sceneDiffs);
        result.put("feeDiffs", feeDiffs);
        result.put("ruleDiffs", ruleDiffs);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishScene(CostPublishCreateBo bo) {
        return distributedLockSupport.executeSceneVersioningLock(bo.getSceneId(),
                "当前场景正在执行发布/生效/回滚，请稍后重试", () ->
                {
                    Map<String, Object> precheck = selectPublishPrecheck(bo.getSceneId());
                    if (!Boolean.TRUE.equals(precheck.get("publishable"))) {
                        throw new ServiceException(buildPublishBlockedMessage(precheck));
                    }

                    PublishSnapshotBundle draftBundle = buildDraftSnapshotBundle(bo.getSceneId());
                    String operator = SecurityUtils.getUsername();
                    Date now = DateUtils.getNowDate();

                    CostPublishVersion version = new CostPublishVersion();
                    version.setSceneId(bo.getSceneId());
                    version.setVersionNo(buildNextVersionNo(bo.getSceneId(), now));
                    version.setVersionStatus(VERSION_STATUS_PUBLISHED);
                    version.setPublishDesc(bo.getPublishDesc());
                    version.setValidationResultJson(PublishJsonSupport.writeJson(precheck));
                    version.setSnapshotHash(draftBundle.snapshotHash);
                    version.setPublishedBy(operator);
                    version.setPublishedTime(now);
                    publishVersionMapper.insert(version);

                    for (CostPublishSnapshot snapshot : draftBundle.snapshotRows) {
                        snapshot.setSnapshotId(null);
                        snapshot.setVersionId(version.getVersionId());
                        publishSnapshotMapper.insert(snapshot);
                    }

                    if (Boolean.TRUE.equals(bo.getActivateNow())) {
                        activateVersionInternal(version, false, operator, now);
                    }
                    return 1;
                });
    }

    @SuppressWarnings("unchecked")
    private String buildPublishBlockedMessage(Map<String, Object> precheck) {
        Object blockingItemsValue = precheck == null ? null : precheck.get("blockingItems");
        if (!(blockingItemsValue instanceof List)) {
            return "当前场景存在阻断项，请先处理后再发布";
        }
        List<CostPublishCheckItemVo> blockingItems = (List<CostPublishCheckItemVo>) blockingItemsValue;
        if (blockingItems.isEmpty()) {
            return "当前场景存在阻断项，请先处理后再发布";
        }
        String detail = blockingItems.stream()
                .map(item -> item.getTitle() + "：" + item.getMessage())
                .collect(Collectors.joining("；"));
        return "当前场景存在" + blockingItems.size() + "项发布阻断，请一次性处理后再发布：" + detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activateVersion(Long versionId) {
        CostPublishVersion version = requireVersion(versionId);
        return distributedLockSupport.executeSceneVersioningLock(version.getSceneId(),
                "当前场景正在执行发布/生效/回滚，请稍后重试", () ->
                {
                    activateVersionInternal(version, false, SecurityUtils.getUsername(), DateUtils.getNowDate());
                    return 1;
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackVersion(Long versionId) {
        CostPublishVersion version = requireVersion(versionId);
        return distributedLockSupport.executeSceneVersioningLock(version.getSceneId(),
                "当前场景正在执行发布/生效/回滚，请稍后重试", () ->
                {
                    activateVersionInternal(version, true, SecurityUtils.getUsername(), DateUtils.getNowDate());
                    return 1;
                });
    }


    private void activateVersionInternal(CostPublishVersion targetVersion, boolean rollback, String operator, Date operateTime) {
        CostScene scene = sceneMapper.selectById(targetVersion.getSceneId());
        if (StringUtils.isNull(scene)) {
            throw new ServiceException("所属场景不存在，无法切换版本");
        }
        CostPublishVersion currentActive = publishVersionMapper.selectActiveVersionByScene(targetVersion.getSceneId());
        if (currentActive != null && Objects.equals(currentActive.getVersionId(), targetVersion.getVersionId())) {
            if (rollback) {
                throw new ServiceException("当前版本已经是生效版本，无需回滚");
            }
            return;
        }
        if (!rollback && !StringUtils.equalsAny(targetVersion.getVersionStatus(), VERSION_STATUS_PUBLISHED, VERSION_STATUS_ROLLED_BACK)) {
            throw new ServiceException("只有已发布或已回滚的版本才能设为生效");
        }
        if (rollback && !StringUtils.equalsAny(targetVersion.getVersionStatus(), VERSION_STATUS_PUBLISHED, VERSION_STATUS_ROLLED_BACK)) {
            throw new ServiceException("只有历史发布版本才能作为回滚目标");
        }
        if (currentActive != null) {
            publishVersionMapper.update(null, Wrappers.<CostPublishVersion>lambdaUpdate()
                    .eq(CostPublishVersion::getVersionId, currentActive.getVersionId())
                    .set(CostPublishVersion::getVersionStatus, rollback ? VERSION_STATUS_ROLLED_BACK : VERSION_STATUS_PUBLISHED)
                    .set(rollback, CostPublishVersion::getRollbackBy, operator)
                    .set(rollback, CostPublishVersion::getRollbackTime, operateTime)
                    .set(CostPublishVersion::getUpdateBy, operator)
                    .set(CostPublishVersion::getUpdateTime, operateTime));
        }
        publishVersionMapper.update(null, Wrappers.<CostPublishVersion>lambdaUpdate()
                .eq(CostPublishVersion::getVersionId, targetVersion.getVersionId())
                .set(CostPublishVersion::getVersionStatus, VERSION_STATUS_ACTIVE)
                .set(CostPublishVersion::getActivatedBy, operator)
                .set(CostPublishVersion::getActivatedTime, operateTime)
                .set(CostPublishVersion::getUpdateBy, operator)
                .set(CostPublishVersion::getUpdateTime, operateTime));
        sceneMapper.update(null, Wrappers.<CostScene>lambdaUpdate()
                .eq(CostScene::getSceneId, targetVersion.getSceneId())
                .set(CostScene::getActiveVersionId, targetVersion.getVersionId())
                .set(CostScene::getUpdateBy, operator)
                .set(CostScene::getUpdateTime, operateTime));
    }

    private PublishSnapshotBundle buildDraftSnapshotBundle(Long sceneId) {
        CostScene scene = sceneMapper.selectById(sceneId);
        if (StringUtils.isNull(scene)) {
            throw new ServiceException("场景不存在，请重新选择");
        }

        CostFeeItem feeQuery = new CostFeeItem();
        feeQuery.setSceneId(sceneId);
        feeQuery.setStatus(STATUS_ENABLED);
        List<CostFeeItem> feeItems = feeMapper.selectFeeOptions(feeQuery);
        Map<Long, CostFeeItem> feeById = feeItems.stream().collect(Collectors.toMap(CostFeeItem::getFeeId, item -> item, (a, b) -> a, LinkedHashMap::new));

        CostVariable variableQuery = new CostVariable();
        variableQuery.setSceneId(sceneId);
        variableQuery.setStatus(STATUS_ENABLED);
        List<CostVariable> variables = variableMapper.selectVariableOptions(variableQuery);

        CostFormula formulaQuery = new CostFormula();
        formulaQuery.setSceneId(sceneId);
        formulaQuery.setStatus(STATUS_ENABLED);
        List<CostFormula> formulas = formulaMapper.selectFormulaOptions(formulaQuery);

        CostRule ruleQuery = new CostRule();
        ruleQuery.setSceneId(sceneId);
        ruleQuery.setStatus(STATUS_ENABLED);
        List<CostRule> rules = ruleMapper.selectRuleList(ruleQuery).stream()
                .filter(item -> feeById.containsKey(item.getFeeId()))
                .sorted(Comparator.comparing(CostRule::getRuleCode, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        PublishSnapshotBundle bundle = new PublishSnapshotBundle();
        bundle.sceneId = scene.getSceneId();
        bundle.sceneCode = scene.getSceneCode();
        bundle.sceneName = scene.getSceneName();
        bundle.sceneSnapshot = buildSceneSnapshot(scene);
        addSceneSnapshot(bundle, scene);
        addFeeSnapshots(bundle, feeItems);
        addVariableSnapshots(bundle, variables);
        addFormulaSnapshots(bundle, formulas);
        addRuleSnapshots(bundle, rules);
        addConditionSnapshots(bundle, publishVersionMapper.selectRuleConditionsForPublish(sceneId));
        addTierSnapshots(bundle, publishVersionMapper.selectRuleTiersForPublish(sceneId));
        bundle.snapshotHash = buildSnapshotHash(bundle.snapshotRows);
        return bundle;
    }

    private PublishSnapshotBundle loadSnapshotBundle(Long versionId) {
        CostPublishVersion version = requireVersion(versionId);
        PublishSnapshotBundle bundle = new PublishSnapshotBundle();
        bundle.sceneId = version.getSceneId();
        bundle.sceneCode = version.getSceneCode();
        bundle.sceneName = version.getSceneName();
        bundle.snapshotHash = version.getSnapshotHash();
        bundle.snapshotRows = publishVersionMapper.selectSnapshotList(versionId, null);
        for (CostPublishSnapshot snapshot : bundle.snapshotRows) {
            Map<String, Object> json = PublishJsonSupport.parseJsonMap(snapshot.getSnapshotJson());
            if (SNAPSHOT_SCENE.equals(snapshot.getSnapshotType())) {
                bundle.sceneSnapshot = json;
            } else if (SNAPSHOT_FEE.equals(snapshot.getSnapshotType())) {
                bundle.feesByCode.put(snapshot.getObjectCode(), json);
            } else if (SNAPSHOT_VARIABLE.equals(snapshot.getSnapshotType())) {
                bundle.variablesByCode.put(snapshot.getObjectCode(), json);
            } else if (SNAPSHOT_FORMULA.equals(snapshot.getSnapshotType())) {
                bundle.formulasByCode.put(snapshot.getObjectCode(), json);
            } else if (SNAPSHOT_RULE.equals(snapshot.getSnapshotType())) {
                bundle.rulesByCode.put(snapshot.getObjectCode(), json);
                String feeCode = stringValue(json.get("feeCode"));
                bundle.ruleToFeeCode.put(snapshot.getObjectCode(), feeCode);
                bundle.feeRuleCodes.computeIfAbsent(feeCode, key -> new TreeSet<>()).add(snapshot.getObjectCode());
                addRefVariable(bundle, feeCode, stringValue(json.get("quantityVariableCode")));
            } else if (SNAPSHOT_RULE_CONDITION.equals(snapshot.getSnapshotType())) {
                String ruleCode = stringValue(json.get("ruleCode"));
                String feeCode = stringValue(json.get("feeCode"));
                bundle.ruleConditionsByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(json);
                addRefVariable(bundle, feeCode, stringValue(json.get("variableCode")));
            } else if (SNAPSHOT_RULE_TIER.equals(snapshot.getSnapshotType())) {
                String ruleCode = stringValue(json.get("ruleCode"));
                bundle.ruleTiersByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(json);
            }
        }
        sortBundleCollections(bundle);
        return bundle;
    }

    private void addSceneSnapshot(PublishSnapshotBundle bundle, CostScene scene) {
        CostPublishSnapshot snapshot = new CostPublishSnapshot();
        snapshot.setSnapshotType(SNAPSHOT_SCENE);
        snapshot.setObjectCode(scene.getSceneCode());
        snapshot.setObjectName(scene.getSceneName());
        snapshot.setSnapshotJson(PublishJsonSupport.writeJson(bundle.sceneSnapshot));
        snapshot.setSortNo(1);
        bundle.snapshotRows.add(snapshot);
    }

    private void addFeeSnapshots(PublishSnapshotBundle bundle, List<CostFeeItem> feeItems) {
        int sortNo = 10;
        for (CostFeeItem fee : feeItems) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("feeCode", fee.getFeeCode());
            json.put("feeName", fee.getFeeName());
            json.put("feeCategory", fee.getFeeCategory());
            json.put("unitCode", fee.getUnitCode());
            json.put("factorSummary", fee.getFactorSummary());
            json.put("scopeDescription", fee.getScopeDescription());
            json.put("objectDimension", fee.getObjectDimension());
            json.put("sortNo", fee.getSortNo());
            json.put("status", fee.getStatus());
            json.put("remark", fee.getRemark());
            bundle.feesByCode.put(fee.getFeeCode(), json);

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_FEE);
            snapshot.setObjectCode(fee.getFeeCode());
            snapshot.setObjectName(fee.getFeeName());
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private void addVariableSnapshots(PublishSnapshotBundle bundle, List<CostVariable> variables) {
        int sortNo = 1000;
        for (CostVariable variable : variables) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("variableCode", variable.getVariableCode());
            json.put("variableName", variable.getVariableName());
            json.put("variableType", variable.getVariableType());
            json.put("sourceType", variable.getSourceType());
            json.put("sourceSystem", variable.getSourceSystem());
            json.put("dictType", variable.getDictType());
            json.put("remoteApi", variable.getRemoteApi());
            json.put("authType", variable.getAuthType());
            json.put("authConfigJson", variable.getAuthConfigJson());
            json.put("dataPath", variable.getDataPath());
            json.put("mappingConfigJson", variable.getMappingConfigJson());
            json.put("syncMode", variable.getSyncMode());
            json.put("cachePolicy", variable.getCachePolicy());
            json.put("fallbackPolicy", variable.getFallbackPolicy());
            json.put("formulaExpr", variable.getFormulaExpr());
            json.put("formulaCode", variable.getFormulaCode());
            json.put("dataType", variable.getDataType());
            json.put("defaultValue", variable.getDefaultValue());
            json.put("precisionScale", variable.getPrecisionScale());
            json.put("sortNo", variable.getSortNo());
            json.put("status", variable.getStatus());
            json.put("remark", variable.getRemark());
            bundle.variablesByCode.put(variable.getVariableCode(), json);

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_VARIABLE);
            snapshot.setObjectCode(variable.getVariableCode());
            snapshot.setObjectName(variable.getVariableName());
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private void addFormulaSnapshots(PublishSnapshotBundle bundle, List<CostFormula> formulas) {
        int sortNo = 1500;
        for (CostFormula formula : formulas) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("formulaCode", formula.getFormulaCode());
            json.put("formulaName", formula.getFormulaName());
            json.put("formulaDesc", formula.getFormulaDesc());
            json.put("businessFormula", formula.getBusinessFormula());
            json.put("formulaExpr", formula.getFormulaExpr());
            json.put("namespaceScope", formula.getNamespaceScope());
            json.put("returnType", formula.getReturnType());
            json.put("sortNo", formula.getSortNo());
            json.put("status", formula.getStatus());
            json.put("remark", formula.getRemark());
            bundle.formulasByCode.put(formula.getFormulaCode(), json);

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_FORMULA);
            snapshot.setObjectCode(formula.getFormulaCode());
            snapshot.setObjectName(formula.getFormulaName());
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private void addRuleSnapshots(PublishSnapshotBundle bundle, List<CostRule> rules) {
        int sortNo = 2000;
        for (CostRule rule : rules) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("ruleCode", rule.getRuleCode());
            json.put("ruleName", rule.getRuleName());
            json.put("feeCode", rule.getFeeCode());
            json.put("feeName", rule.getFeeName());
            json.put("ruleType", rule.getRuleType());
            json.put("conditionLogic", rule.getConditionLogic());
            json.put("priority", rule.getPriority());
            json.put("quantityVariableCode", rule.getQuantityVariableCode());
            json.put("pricingMode", rule.getPricingMode());
            json.put("pricingJson", PublishJsonSupport.parseJsonMap(rule.getPricingJson()));
            json.put("amountFormula", rule.getAmountFormula());
            json.put("amountFormulaCode", rule.getAmountFormulaCode());
            json.put("amountBusinessFormula", rule.getAmountBusinessFormula());
            json.put("noteTemplate", rule.getNoteTemplate());
            json.put("sortNo", rule.getSortNo());
            json.put("status", rule.getStatus());
            json.put("remark", rule.getRemark());
            bundle.rulesByCode.put(rule.getRuleCode(), json);
            bundle.ruleToFeeCode.put(rule.getRuleCode(), rule.getFeeCode());
            bundle.feeRuleCodes.computeIfAbsent(rule.getFeeCode(), key -> new TreeSet<>()).add(rule.getRuleCode());
            addRefVariable(bundle, rule.getFeeCode(), rule.getQuantityVariableCode());

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_RULE);
            snapshot.setObjectCode(rule.getRuleCode());
            snapshot.setObjectName(rule.getRuleName());
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private void addConditionSnapshots(PublishSnapshotBundle bundle, List<Map<String, Object>> rows) {
        int sortNo = 3000;
        for (Map<String, Object> source : rows) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("ruleCode", source.get("ruleCode"));
            json.put("ruleName", source.get("ruleName"));
            json.put("feeCode", source.get("feeCode"));
            json.put("feeName", source.get("feeName"));
            json.put("groupNo", source.get("groupNo"));
            json.put("sortNo", source.get("sortNo"));
            json.put("variableCode", source.get("variableCode"));
            json.put("displayName", source.get("displayName"));
            json.put("operatorCode", source.get("operatorCode"));
            json.put("compareValue", source.get("compareValue"));
            json.put("status", source.get("status"));
            json.put("remark", source.get("remark"));

            String ruleCode = stringValue(source.get("ruleCode"));
            String feeCode = stringValue(source.get("feeCode"));
            bundle.ruleConditionsByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(json);
            addRefVariable(bundle, feeCode, stringValue(source.get("variableCode")));

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_RULE_CONDITION);
            snapshot.setObjectCode(ruleCode + "#C" + source.get("sortNo"));
            snapshot.setObjectName(stringValue(source.get("displayName")));
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private void addTierSnapshots(PublishSnapshotBundle bundle, List<Map<String, Object>> rows) {
        int sortNo = 4000;
        for (Map<String, Object> source : rows) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("ruleCode", source.get("ruleCode"));
            json.put("ruleName", source.get("ruleName"));
            json.put("feeCode", source.get("feeCode"));
            json.put("feeName", source.get("feeName"));
            json.put("tierNo", source.get("tierNo"));
            json.put("startValue", source.get("startValue"));
            json.put("endValue", source.get("endValue"));
            json.put("rateValue", source.get("rateValue"));
            json.put("intervalMode", source.get("intervalMode"));
            json.put("status", source.get("status"));
            json.put("remark", source.get("remark"));

            String ruleCode = stringValue(source.get("ruleCode"));
            bundle.ruleTiersByRuleCode.computeIfAbsent(ruleCode, key -> new ArrayList<>()).add(json);

            CostPublishSnapshot snapshot = new CostPublishSnapshot();
            snapshot.setSnapshotType(SNAPSHOT_RULE_TIER);
            snapshot.setObjectCode(ruleCode + "#T" + source.get("tierNo"));
            snapshot.setObjectName(ruleCode + "阶梯" + source.get("tierNo"));
            snapshot.setSnapshotJson(PublishJsonSupport.writeJson(json));
            snapshot.setSortNo(sortNo++);
            bundle.snapshotRows.add(snapshot);
        }
    }

    private Map<String, Object> buildSceneSnapshot(CostScene scene) {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.put("sceneCode", scene.getSceneCode());
        json.put("sceneName", scene.getSceneName());
        json.put("businessDomain", scene.getBusinessDomain());
        json.put("orgCode", scene.getOrgCode());
        json.put("sceneType", scene.getSceneType());
        json.put("defaultObjectDimension", scene.getDefaultObjectDimension());
        json.put("status", scene.getStatus());
        json.put("remark", scene.getRemark());
        return json;
    }

    private void sortBundleCollections(PublishSnapshotBundle bundle) {
        for (List<Map<String, Object>> items : bundle.ruleConditionsByRuleCode.values()) {
            items.sort(Comparator.comparing((Map<String, Object> item) -> stringValue(item.get("ruleCode")))
                    .thenComparing(item -> stringValue(item.get("groupNo")))
                    .thenComparing(item -> stringValue(item.get("sortNo"))));
        }
        for (List<Map<String, Object>> items : bundle.ruleTiersByRuleCode.values()) {
            items.sort(Comparator.comparing((Map<String, Object> item) -> stringValue(item.get("ruleCode")))
                    .thenComparing(item -> stringValue(item.get("tierNo"))));
        }
    }

    private String buildNextVersionNo(Long sceneId, Date now) {
        String prefix = "V" + DateUtils.parseDateToStr("yyyy.MM", now) + ".";
        Long sequence = publishVersionMapper.selectMonthlyVersionSequence(sceneId, prefix);
        long next = sequence == null ? 1L : sequence + 1L;
        return prefix + VERSION_SEQ_FORMAT.format(next);
    }

    private String buildSnapshotHash(List<CostPublishSnapshot> snapshots) {
        List<String> pieces = snapshots.stream()
                .sorted(Comparator.comparing(CostPublishSnapshot::getSnapshotType)
                        .thenComparing(CostPublishSnapshot::getObjectCode, Comparator.nullsLast(String::compareTo)))
                .map(item -> item.getSnapshotType() + "|" + item.getObjectCode() + "|"
                        + PublishJsonSupport.canonicalJson(PublishJsonSupport.parseJsonMap(item.getSnapshotJson())))
                .collect(Collectors.toList());
        return sha256(String.join("\n", pieces));
    }

    private CostPublishVersion requireVersion(Long versionId) {
        CostPublishVersion version = publishVersionMapper.selectPublishVersionDetail(versionId);
        if (StringUtils.isNull(version)) {
            throw new ServiceException("发布版本不存在，请刷新后重试");
        }
        return version;
    }


    private void addRefVariable(PublishSnapshotBundle bundle, String feeCode, String variableCode) {
        if (StringUtils.isEmpty(feeCode) || StringUtils.isEmpty(variableCode)) {
            return;
        }
        bundle.feeReferencedVariables.computeIfAbsent(feeCode, key -> new TreeSet<>()).add(variableCode);
    }

    private long getLongValue(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(map.get(key)));
    }

    /**
     * 解析最近一次发布校验结果编码。
     *
     * @param blockingCount 阻断数量
     * @param warningCount  告警数量
     * @param latestVersion 最近版本
     *
     * @return 校验结果
     */
    private String resolveCheckResult(long blockingCount, long warningCount, CostPublishVersion latestVersion) {
        if (latestVersion == null) {
            return "UNAVAILABLE";
        }
        return PublishValidationSupport.resolveCheckResult(blockingCount, warningCount);
    }

    /**
     * 解析最近一次发布校验结果文案。
     *
     * @param blockingCount 阻断数量
     * @param warningCount  告警数量
     * @param latestVersion 最近版本
     *
     * @return 文案
     */
    private String resolveCheckLabel(long blockingCount, long warningCount, CostPublishVersion latestVersion) {
        if (latestVersion == null) {
            return "暂无校验";
        }
        return PublishValidationSupport.resolveCheckLabel(blockingCount, warningCount);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("系统不支持 SHA-256 摘要算法");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
