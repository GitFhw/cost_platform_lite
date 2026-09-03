package com.ruoyi.system.service.impl.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostOpenApp;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.vo.CostOpenAppSession;
import com.ruoyi.system.mapper.cost.CostOpenAppMapper;
import com.ruoyi.system.service.cost.ICostOpenAppService;
import com.ruoyi.system.service.cost.ICostSceneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ruoyi.system.service.cost.constant.CostDomainConstants.STATUS_ENABLED;

/**
 * 开放应用服务实现
 */
@Service
public class CostOpenAppServiceImpl implements ICostOpenAppService {
    public static final String SCENE_SCOPE_ALL = "ALL";
    public static final String SCENE_SCOPE_LIST = "LIST";

    private static final int DEFAULT_TOKEN_TTL_SECONDS = 7200;
    private static final int MIN_TOKEN_TTL_SECONDS = 300;
    private static final int MAX_TOKEN_TTL_SECONDS = 86400;
    private static final int SECRET_LENGTH = 32;
    private static final char[] SECRET_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    @Autowired
    private CostOpenAppMapper openAppMapper;

    @Autowired
    private ICostSceneService sceneService;

    @Autowired
    private ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<CostOpenApp> selectOpenAppList(CostOpenApp query) {
        List<CostOpenApp> appList = openAppMapper.selectList(buildListWrapper(query));
        hydrateSceneMetadata(appList);
        return appList;
    }

    @Override
    public CostOpenApp selectOpenAppById(Long appId) {
        if (appId == null) {
            return null;
        }
        CostOpenApp app = openAppMapper.selectById(appId);
        if (app == null) {
            return null;
        }
        hydrateSceneMetadata(Collections.singletonList(app));
        return app;
    }

    @Override
    public boolean checkOpenAppCodeUnique(CostOpenApp app) {
        if (app == null || StringUtils.isBlank(app.getAppCode())) {
            return true;
        }
        Long currentId = app.getAppId() == null ? -1L : app.getAppId();
        Long count = openAppMapper.selectCount(Wrappers.<CostOpenApp>lambdaQuery()
                .eq(CostOpenApp::getAppCode, StringUtils.trim(app.getAppCode()))
                .ne(currentId != -1L, CostOpenApp::getAppId, currentId));
        return count == null || count == 0;
    }

    @Override
    public CostOpenApp insertOpenApp(CostOpenApp app) {
        normalizeOpenApp(app);
        validateOpenApp(app);
        String plaintextSecret = generateSecret();
        app.setAppSecretHash(sha256Hex(plaintextSecret));
        openAppMapper.insert(app);
        CostOpenApp saved = selectOpenAppById(app.getAppId());
        if (saved != null) {
            saved.setAppSecretPlaintext(plaintextSecret);
        }
        return saved;
    }

    @Override
    public int updateOpenApp(CostOpenApp app) {
        normalizeOpenApp(app);
        validateOpenApp(app);
        CostOpenApp current = requireOpenApp(app.getAppId());
        app.setAppSecretHash(current.getAppSecretHash());
        return openAppMapper.updateById(app);
    }

    @Override
    public CostOpenApp resetOpenAppSecret(Long appId, String updateBy) {
        CostOpenApp current = requireOpenApp(appId);
        String plaintextSecret = generateSecret();
        current.setAppSecretHash(sha256Hex(plaintextSecret));
        current.setUpdateBy(updateBy);
        openAppMapper.updateById(current);
        CostOpenApp saved = selectOpenAppById(appId);
        if (saved != null) {
            saved.setAppSecretPlaintext(plaintextSecret);
        }
        return saved;
    }

    @Override
    public int deleteOpenAppByIds(Long[] appIds) {
        if (appIds == null || appIds.length == 0) {
            return 0;
        }
        return openAppMapper.deleteBatchIds(Arrays.asList(appIds));
    }

    @Override
    public CostOpenApp authenticateApp(String appCode, String appSecret) {
        if (StringUtils.isBlank(appCode) || StringUtils.isBlank(appSecret)) {
            throw new ServiceException("开放应用编码和密钥不能为空", HttpStatus.UNAUTHORIZED);
        }
        CostOpenApp app = openAppMapper.selectOne(Wrappers.<CostOpenApp>lambdaQuery()
                .eq(CostOpenApp::getAppCode, StringUtils.trim(appCode)));
        if (app == null) {
            throw new ServiceException("开放应用身份无效，请检查 appCode 或 appSecret", HttpStatus.UNAUTHORIZED);
        }
        validateAppStatus(app);
        String requestHash = sha256Hex(StringUtils.trim(appSecret));
        if (!MessageDigest.isEqual(
                StringUtils.defaultString(requestHash).getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(app.getAppSecretHash()).getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException("开放应用身份无效，请检查 appCode 或 appSecret", HttpStatus.UNAUTHORIZED);
        }
        return app;
    }

    @Override
    public CostOpenAppSession buildSession(CostOpenApp app) {
        CostOpenAppSession session = new CostOpenAppSession();
        session.setAppId(app.getAppId());
        session.setAppCode(app.getAppCode());
        session.setAppName(app.getAppName());
        session.setSceneScopeType(normalizeSceneScopeType(app.getSceneScopeType()));
        session.setAuthorizedSceneIds(parseSceneIds(app.getSceneIdsJson()));
        session.setAllowDraftSnapshot(Boolean.TRUE.equals(app.getAllowDraftSnapshot()));
        session.setTokenTtlSeconds(resolveTokenTtlSeconds(app.getTokenTtlSeconds()));
        return session;
    }

    @Override
    public boolean canAccessScene(CostOpenAppSession session, Long sceneId) {
        if (session == null || sceneId == null) {
            return false;
        }
        if (SCENE_SCOPE_ALL.equalsIgnoreCase(normalizeSceneScopeType(session.getSceneScopeType()))) {
            return true;
        }
        return resolveAuthorizedSceneIds(session).contains(sceneId);
    }

    @Override
    public void assertCanAccessScene(CostOpenAppSession session, Long sceneId) {
        if (!canAccessScene(session, sceneId)) {
            throw new ServiceException("当前开放应用未被授权访问该场景，请联系平台管理员开通场景权限", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public boolean allowDraftSnapshot(CostOpenAppSession session) {
        return session != null && Boolean.TRUE.equals(session.getAllowDraftSnapshot());
    }

    @Override
    public Set<Long> resolveAuthorizedSceneIds(CostOpenAppSession session) {
        if (session == null || session.getAuthorizedSceneIds() == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(session.getAuthorizedSceneIds());
    }

    private LambdaQueryWrapper<CostOpenApp> buildListWrapper(CostOpenApp query) {
        LambdaQueryWrapper<CostOpenApp> wrapper = Wrappers.lambdaQuery();
        if (query == null) {
            return wrapper.orderByDesc(CostOpenApp::getUpdateTime, CostOpenApp::getCreateTime);
        }
        String keyword = StringUtils.trim(query.getSearchValue());
        wrapper.and(StringUtils.isNotBlank(keyword), item -> item
                .like(CostOpenApp::getAppCode, keyword)
                .or()
                .like(CostOpenApp::getAppName, keyword)
                .or()
                .like(CostOpenApp::getRemark, keyword));
        wrapper.eq(StringUtils.isNotBlank(query.getStatus()), CostOpenApp::getStatus, query.getStatus());
        wrapper.eq(StringUtils.isNotBlank(query.getSceneScopeType()), CostOpenApp::getSceneScopeType, normalizeSceneScopeType(query.getSceneScopeType()));
        wrapper.eq(query.getAllowDraftSnapshot() != null, CostOpenApp::getAllowDraftSnapshot, query.getAllowDraftSnapshot());
        return wrapper.orderByDesc(CostOpenApp::getUpdateTime, CostOpenApp::getCreateTime);
    }

    private void normalizeOpenApp(CostOpenApp app) {
        if (app == null) {
            return;
        }
        app.setAppCode(StringUtils.trim(app.getAppCode()));
        app.setAppName(StringUtils.trim(app.getAppName()));
        app.setSceneScopeType(normalizeSceneScopeType(app.getSceneScopeType()));
        app.setAllowDraftSnapshot(Boolean.TRUE.equals(app.getAllowDraftSnapshot()));
        app.setTokenTtlSeconds(resolveTokenTtlSeconds(app.getTokenTtlSeconds()));
        app.setSceneIdsJson(buildSceneIdsJson(app.getSceneScopeType(), app.getSceneIds()));
    }

    private void validateOpenApp(CostOpenApp app) {
        if (app == null) {
            throw new ServiceException("开放应用配置不能为空");
        }
        if (StringUtils.isBlank(app.getAppCode())) {
            throw new ServiceException("开放应用编码不能为空");
        }
        if (StringUtils.isBlank(app.getAppName())) {
            throw new ServiceException("开放应用名称不能为空");
        }
        if (!checkOpenAppCodeUnique(app)) {
            throw new ServiceException("开放应用编码已存在，请调整后重试");
        }
        if (SCENE_SCOPE_LIST.equalsIgnoreCase(app.getSceneScopeType())
                && (app.getSceneIds() == null || app.getSceneIds().isEmpty())) {
            throw new ServiceException("指定场景授权时至少需要选择一个可访问场景");
        }
        if (app.getEffectiveStartTime() != null
                && app.getEffectiveEndTime() != null
                && app.getEffectiveStartTime().after(app.getEffectiveEndTime())) {
            throw new ServiceException("失效时间不能早于生效时间");
        }
    }

    private CostOpenApp requireOpenApp(Long appId) {
        CostOpenApp app = selectOpenAppById(appId);
        if (app == null) {
            throw new ServiceException("开放应用不存在或已被删除");
        }
        return app;
    }

    private void hydrateSceneMetadata(List<CostOpenApp> appList) {
        if (appList == null || appList.isEmpty()) {
            return;
        }
        Set<Long> sceneIds = new LinkedHashSet<>();
        for (CostOpenApp app : appList) {
            List<Long> ids = parseSceneIds(app.getSceneIdsJson());
            app.setSceneIds(ids);
            sceneIds.addAll(ids);
        }
        Map<Long, String> sceneNameMap = loadSceneNameMap(sceneIds);
        for (CostOpenApp app : appList) {
            List<String> sceneNames = app.getSceneIds() == null ? Collections.emptyList() : app.getSceneIds().stream()
                    .map(sceneNameMap::get)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            app.setSceneNames(sceneNames);
            app.setSceneCount(app.getSceneIds() == null ? 0 : app.getSceneIds().size());
            app.setSceneNamesSummary(buildSceneNamesSummary(app.getSceneScopeType(), sceneNames, app.getSceneCount()));
        }
    }

    private Map<Long, String> loadSceneNameMap(Set<Long> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return Collections.emptyMap();
        }
        CostScene query = new CostScene();
        List<CostScene> sceneList = sceneService.selectSceneOptions(query);
        Map<Long, String> sceneNameMap = new LinkedHashMap<>();
        for (CostScene scene : sceneList) {
            if (scene.getSceneId() == null || !sceneIds.contains(scene.getSceneId())) {
                continue;
            }
            sceneNameMap.put(scene.getSceneId(), String.format("%s / %s", scene.getSceneName(), scene.getSceneCode()));
        }
        return sceneNameMap;
    }

    private String buildSceneNamesSummary(String sceneScopeType, List<String> sceneNames, Integer sceneCount) {
        if (SCENE_SCOPE_ALL.equalsIgnoreCase(normalizeSceneScopeType(sceneScopeType))) {
            return "全部场景";
        }
        if (sceneNames == null || sceneNames.isEmpty()) {
            return "未配置授权场景";
        }
        if (sceneNames.size() <= 2) {
            return String.join("、", sceneNames);
        }
        return sceneNames.get(0) + " 等 " + sceneCount + " 个场景";
    }

    private void validateAppStatus(CostOpenApp app) {
        if (!STATUS_ENABLED.equals(app.getStatus())) {
            throw new ServiceException("开放应用已停用，请联系平台管理员确认", HttpStatus.FORBIDDEN);
        }
        Date now = new Date();
        if (app.getEffectiveStartTime() != null && now.before(app.getEffectiveStartTime())) {
            throw new ServiceException("开放应用尚未生效，请在生效时间后重新申请访问令牌", HttpStatus.FORBIDDEN);
        }
        if (app.getEffectiveEndTime() != null && now.after(app.getEffectiveEndTime())) {
            throw new ServiceException("开放应用已过期，请联系平台管理员续期后重新申请访问令牌", HttpStatus.FORBIDDEN);
        }
    }

    private String normalizeSceneScopeType(String sceneScopeType) {
        return SCENE_SCOPE_LIST.equalsIgnoreCase(sceneScopeType) ? SCENE_SCOPE_LIST : SCENE_SCOPE_ALL;
    }

    private Integer resolveTokenTtlSeconds(Integer tokenTtlSeconds) {
        if (tokenTtlSeconds == null || tokenTtlSeconds <= 0) {
            return DEFAULT_TOKEN_TTL_SECONDS;
        }
        return Math.max(MIN_TOKEN_TTL_SECONDS, Math.min(tokenTtlSeconds, MAX_TOKEN_TTL_SECONDS));
    }

    private List<Long> parseSceneIds(String sceneIdsJson) {
        if (StringUtils.isBlank(sceneIdsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Long> sceneIds = objectMapper.readValue(sceneIdsJson, new TypeReference<List<Long>>() {
            });
            return sceneIds == null ? Collections.emptyList() : sceneIds.stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new ServiceException("开放应用场景权限配置格式不正确，请联系平台管理员修复", HttpStatus.FORBIDDEN);
        }
    }

    private String buildSceneIdsJson(String sceneScopeType, List<Long> sceneIds) {
        if (!SCENE_SCOPE_LIST.equalsIgnoreCase(normalizeSceneScopeType(sceneScopeType))) {
            return "[]";
        }
        List<Long> normalizedSceneIds = sceneIds == null ? Collections.emptyList() : sceneIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalizedSceneIds);
        } catch (Exception e) {
            throw new ServiceException("开放应用场景权限写入失败，请检查授权场景配置");
        }
    }

    private String generateSecret() {
        StringBuilder builder = new StringBuilder(SECRET_LENGTH);
        for (int index = 0; index < SECRET_LENGTH; index++) {
            builder.append(SECRET_CHARS[secureRandom.nextInt(SECRET_CHARS.length)]);
        }
        return builder.toString();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("系统不支持 SHA-256 摘要算法");
        }
    }
}
