package com.ruoyi.system.domain.cost.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 第三方开放应用换取访问令牌请求
 */
@Data
public class CostOpenTokenApplyBo {
    @NotBlank(message = "开放应用编码不能为空")
    private String appCode;

    @NotBlank(message = "开放应用密钥不能为空")
    private String appSecret;
}
