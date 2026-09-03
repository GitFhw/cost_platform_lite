package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.bo.CostOpenTokenApplyBo;
import com.ruoyi.system.domain.cost.vo.CostOpenAppSession;

import java.util.Map;

/**
 * 开放接口令牌服务接口
 */
public interface ICostOpenTokenService {
    Map<String, Object> issueToken(CostOpenTokenApplyBo bo);

    CostOpenAppSession getSession(String accessToken);
}
