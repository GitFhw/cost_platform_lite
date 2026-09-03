package com.ruoyi.system.service.impl.cost;

import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.cost.CostOpenApp;
import com.ruoyi.system.domain.cost.bo.CostOpenTokenApplyBo;
import com.ruoyi.system.domain.cost.vo.CostOpenAppSession;
import com.ruoyi.system.service.cost.ICostOpenAppService;
import com.ruoyi.system.service.cost.ICostOpenTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 开放接口令牌服务实现
 */
@Service
public class CostOpenTokenServiceImpl implements ICostOpenTokenService {
    private static final String OPEN_TOKEN_CACHE_KEY = "cost_open_tokens:";

    @Autowired
    private ICostOpenAppService openAppService;

    @Autowired
    private RedisCache redisCache;

    @Override
    public Map<String, Object> issueToken(CostOpenTokenApplyBo bo) {
        CostOpenApp app = openAppService.authenticateApp(bo.getAppCode(), bo.getAppSecret());
        CostOpenAppSession session = openAppService.buildSession(app);
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + session.getTokenTtlSeconds() * 1000L);
        session.setIssuedAt(issuedAt);
        session.setExpiresAt(expiresAt);

        String accessToken = "coa_" + IdUtils.fastSimpleUUID();
        redisCache.setCacheObject(buildTokenCacheKey(accessToken), session, session.getTokenTtlSeconds(), TimeUnit.SECONDS);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("appCode", session.getAppCode());
        result.put("appName", session.getAppName());
        result.put("tokenType", "Bearer");
        result.put("accessToken", accessToken);
        result.put("expiresInSeconds", session.getTokenTtlSeconds());
        result.put("issuedAt", issuedAt);
        result.put("expiresAt", expiresAt);
        result.put("draftSnapshotAllowed", session.getAllowDraftSnapshot());
        result.put("sceneScopeType", session.getSceneScopeType());
        result.put("authorizedSceneIds", session.getAuthorizedSceneIds());
        return result;
    }

    @Override
    public CostOpenAppSession getSession(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            throw new ServiceException("开放接口访问令牌不能为空，请先申请 accessToken", HttpStatus.UNAUTHORIZED);
        }
        CostOpenAppSession session = redisCache.getCacheObject(buildTokenCacheKey(accessToken));
        if (session == null) {
            throw new ServiceException("开放接口访问令牌已失效，请重新申请 accessToken", HttpStatus.UNAUTHORIZED);
        }
        return session;
    }

    private String buildTokenCacheKey(String accessToken) {
        return OPEN_TOKEN_CACHE_KEY + accessToken;
    }
}
