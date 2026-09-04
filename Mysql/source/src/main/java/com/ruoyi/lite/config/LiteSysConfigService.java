package com.ruoyi.lite.config;

import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量宿主的最小系统参数实现。
 *
 * <p>计费核心只需要读取告警 Webhook 等少量参数，不应因为没有完整若依系统参数模块而无法启动。
 * 参数仍可通过环境变量和本地内存覆盖；接入完整若依母体时，可移除此实现并使用母体服务。</p>
 */
@Service
@Primary
public class LiteSysConfigService implements ISysConfigService {
    private final Map<String, SysConfig> configs = new ConcurrentHashMap<>();

    @Override
    public SysConfig selectConfigById(Long configId) {
        return configs.values().stream()
                .filter(item -> configId != null && configId.equals(item.getConfigId()))
                .findFirst().orElse(null);
    }

    @Override
    public String selectConfigByKey(String configKey) {
        SysConfig config = configs.get(configKey);
        return config == null || config.getConfigValue() == null ? "" : config.getConfigValue();
    }

    @Override
    public boolean selectCaptchaEnabled() {
        String value = selectConfigByKey("sys.account.captchaEnabled");
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    @Override
    public List<SysConfig> selectConfigList(SysConfig query) {
        List<SysConfig> result = new ArrayList<>(configs.values());
        if (query != null && query.getConfigKey() != null && !query.getConfigKey().trim().isEmpty()) {
            result.removeIf(item -> !query.getConfigKey().equals(item.getConfigKey()));
        }
        result.sort(Comparator.comparing(SysConfig::getConfigId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    @Override
    public int insertConfig(SysConfig config) {
        if (config == null || config.getConfigKey() == null) {
            return 0;
        }
        if (config.getConfigId() == null) {
            config.setConfigId(System.nanoTime());
        }
        configs.put(config.getConfigKey(), config);
        return 1;
    }

    @Override
    public int updateConfig(SysConfig config) {
        return insertConfig(config);
    }

    @Override
    public void deleteConfigByIds(Long[] configIds) {
        if (configIds == null) {
            return;
        }
        for (Long configId : configIds) {
            SysConfig config = selectConfigById(configId);
            if (config != null && !UserConstants.YES.equals(config.getConfigType())) {
                configs.remove(config.getConfigKey());
            }
        }
    }

    @Override
    public void loadingConfigCache() {
        // 轻量版参数按需读取，无需额外初始化。
    }

    @Override
    public void clearConfigCache() {
        configs.clear();
    }

    @Override
    public void resetConfigCache() {
        clearConfigCache();
        loadingConfigCache();
    }

    @Override
    public boolean checkConfigKeyUnique(SysConfig config) {
        if (config == null || config.getConfigKey() == null) {
            return UserConstants.UNIQUE;
        }
        SysConfig current = configs.get(config.getConfigKey());
        return current == null || (config.getConfigId() != null && config.getConfigId().equals(current.getConfigId()));
    }
}
