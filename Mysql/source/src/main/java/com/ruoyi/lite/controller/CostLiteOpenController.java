package com.ruoyi.lite.controller;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lite.plugin.ResultAdapterPlugin;
import com.ruoyi.lite.plugin.CostLitePluginRegistry;
import com.ruoyi.lite.service.CostLiteBillingService;
import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.bo.CostFeeCalculateBo;
import com.ruoyi.system.domain.cost.bo.CostOpenTokenApplyBo;
import com.ruoyi.system.domain.cost.vo.CostOpenAppSession;
import com.ruoyi.system.service.cost.ICostFeeService;
import com.ruoyi.system.service.cost.ICostOpenAppService;
import com.ruoyi.system.service.cost.ICostOpenTokenService;
import com.ruoyi.system.service.cost.ICostRunService;
import com.ruoyi.system.service.cost.ICostSceneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向第三方业务系统的稳定开放接口。
 *
 * <p>开放接口只暴露已授权场景和正式运行能力，配置维护仍走管理接口。</p>
 */
@RestController
@RequestMapping("/cost/open")
public class CostLiteOpenController {
    private static final String TOKEN_HEADER = "X-Cost-Open-Token";
    private static final String SNAPSHOT_MODE_DRAFT = "DRAFT";

    private final ICostOpenTokenService tokenService;
    private final ICostOpenAppService openAppService;
    private final ICostSceneService sceneService;
    private final ICostFeeService feeService;
    private final ICostRunService runService;
    private final CostLiteBillingService billingService;
    private final CostLitePluginRegistry pluginRegistry;

    public CostLiteOpenController(ICostOpenTokenService tokenService,
                                  ICostOpenAppService openAppService,
                                  ICostSceneService sceneService,
                                  ICostFeeService feeService,
                                  ICostRunService runService,
                                  CostLiteBillingService billingService,
                                  CostLitePluginRegistry pluginRegistry) {
        this.tokenService = tokenService;
        this.openAppService = openAppService;
        this.sceneService = sceneService;
        this.feeService = feeService;
        this.runService = runService;
        this.billingService = billingService;
        this.pluginRegistry = pluginRegistry;
    }

    @PostMapping("/auth/token")
    public AjaxResult issueToken(@Valid @RequestBody CostOpenTokenApplyBo request) {
        return AjaxResult.success(tokenService.issueToken(request));
    }

    @GetMapping("/scenes")
    public AjaxResult scenes(HttpServletRequest request) {
        CostOpenAppSession session = requireSession(request);
        List<CostScene> scenes = sceneService.selectSceneOptions(new CostScene());
        scenes.removeIf(scene -> scene == null || !openAppService.canAccessScene(session, scene.getSceneId()));
        return AjaxResult.success(scenes);
    }

    @GetMapping("/scenes/{sceneId}/versions")
    public AjaxResult versions(@PathVariable Long sceneId, HttpServletRequest request) {
        CostOpenAppSession session = requireSession(request);
        assertSceneAccess(session, sceneId);
        return AjaxResult.success(runService.selectVersionOptions(sceneId));
    }

    @GetMapping("/scenes/{sceneId}/fees")
    public AjaxResult fees(@PathVariable Long sceneId,
                           @RequestParam(required = false) Long versionId,
                           @RequestParam(required = false, defaultValue = "ACTIVE") String snapshotMode,
                           HttpServletRequest request) {
        CostOpenAppSession session = requireSession(request);
        assertSceneAccess(session, sceneId);
        assertSnapshotAccess(session, snapshotMode);
        List<Map<String, Object>> runtimeFees = runService.selectRuntimeFeeOptions(sceneId, versionId, normalizeSnapshotMode(snapshotMode));
        if (runtimeFees != null && !runtimeFees.isEmpty()) {
            return AjaxResult.success(runtimeFees);
        }
        CostFeeItem query = new CostFeeItem();
        query.setSceneId(sceneId);
        return AjaxResult.success(feeService.selectFeeOptions(query));
    }

    @GetMapping("/fee-template")
    public AjaxResult feeTemplate(@RequestParam Long sceneId,
                                  @RequestParam(required = false) Long versionId,
                                  @RequestParam(required = false) Long feeId,
                                  @RequestParam(required = false) String feeIds,
                                  @RequestParam(required = false) String feeCode,
                                  @RequestParam(required = false, defaultValue = "FORMAL_SINGLE") String taskType,
                                  @RequestParam(required = false, defaultValue = "ACTIVE") String snapshotMode,
                                  HttpServletRequest request) {
        CostOpenAppSession session = requireSession(request);
        assertSceneAccess(session, sceneId);
        String normalizedMode = normalizeSnapshotMode(snapshotMode);
        assertSnapshotAccess(session, normalizedMode);
        return AjaxResult.success(runService.buildFeeInputTemplate(
                sceneId, versionId, parseIds(feeIds), feeId, feeCode, taskType, normalizedMode));
    }

    @PostMapping("/fee/calculate")
    public AjaxResult calculate(@Valid @RequestBody CostFeeCalculateBo request,
                                HttpServletRequest servletRequest) {
        CostOpenAppSession session = requireSession(servletRequest);
        assertSceneAccess(session, request.getSceneId());
        String normalizedMode = normalizeSnapshotMode(request.getSnapshotMode());
        assertSnapshotAccess(session, normalizedMode);
        request.setSnapshotMode(normalizedMode);
        Map<String, Object> result = billingService.calculate(request);

        String adapterCode = trim(servletRequest.getHeader("X-Cost-Lite-Result-Adapter"));
        if (!adapterCode.isEmpty()) {
            ResultAdapterPlugin adapter = pluginRegistry.getResultAdapter(adapterCode);
            if (adapter == null) {
                throw new ServiceException("结果适配插件不存在：" + adapterCode, HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("appCode", session.getAppCode());
            context.put("sceneId", request.getSceneId());
            context.put("versionId", request.getVersionId());
            return AjaxResult.success(adapter.adapt(result, context));
        }
        return AjaxResult.success(result);
    }

    private CostOpenAppSession requireSession(HttpServletRequest request) {
        String token = trim(request.getHeader(TOKEN_HEADER));
        if (token.isEmpty()) {
            token = trim(request.getHeader("Authorization"));
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = trim(token.substring(7));
            }
        }
        return tokenService.getSession(token);
    }

    private void assertSceneAccess(CostOpenAppSession session, Long sceneId) {
        openAppService.assertCanAccessScene(session, sceneId);
    }

    private void assertSnapshotAccess(CostOpenAppSession session, String snapshotMode) {
        if (SNAPSHOT_MODE_DRAFT.equalsIgnoreCase(snapshotMode) && !openAppService.allowDraftSnapshot(session)) {
            throw new ServiceException("当前开放应用未授权访问草稿版本，请使用 ACTIVE 正式版本", HttpStatus.FORBIDDEN);
        }
    }

    private String normalizeSnapshotMode(String snapshotMode) {
        return SNAPSHOT_MODE_DRAFT.equalsIgnoreCase(snapshotMode) ? SNAPSHOT_MODE_DRAFT : "ACTIVE";
    }

    private List<Long> parseIds(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String item : text.split(",")) {
            if (item != null && !item.isBlank()) {
                try {
                    ids.add(Long.valueOf(item.trim()));
                } catch (NumberFormatException exception) {
                    throw new ServiceException("feeIds 必须是逗号分隔的数字", HttpStatus.BAD_REQUEST);
                }
            }
        }
        return ids;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
