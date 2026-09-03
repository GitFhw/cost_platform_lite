package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostVariable;
import com.ruoyi.system.domain.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 变量中心服务接口。
 *
 * @author HwFan
 */
public interface ICostVariableService {
    /**
     * 查询变量列表。
     */
    List<CostVariable> selectVariableList(CostVariable variable);

    /**
     * 查询变量详情。
     */
    CostVariable selectVariableById(Long variableId);

    /**
     * 查询变量选择框。
     */
    List<CostVariable> selectVariableOptions(CostVariable variable);

    /**
     * 查询变量统计卡片。
     */
    Map<String, Object> selectVariableStats(CostVariable variable);

    /**
     * 查询变量治理预检查。
     */
    CostVariableGovernanceCheckVo selectVariableGovernanceCheck(Long variableId);

    /**
     * 校验变量编码是否唯一。
     */
    boolean checkVariableCodeUnique(CostVariable variable);

    /**
     * 新增变量。
     */
    int insertVariable(CostVariable variable);

    /**
     * 修改变量。
     */
    int updateVariable(CostVariable variable);

    /**
     * 批量删除变量。
     */
    int deleteVariableByIds(Long[] variableIds);

    /**
     * 导入预览与校验报告。
     */
    CostVariableImportPreviewVo previewImport(MultipartFile file) throws Exception;

    CostVariableImportPreviewVo previewImport(MultipartFile file, boolean updateSupport) throws Exception;

    /**
     * 执行变量导入。
     */
    CostVariableImportPreviewVo importVariables(MultipartFile file, boolean updateSupport, String operName) throws Exception;

    /**
     * 复制变量。
     */
    CostVariable copyVariable(CostVariableCopyRequest request);

    /**
     * 查询共享影响因素模板。
     */
    List<CostVariableTemplateVo> selectSharedTemplates();

    /**
     * 应用共享影响因素模板。
     */
    Map<String, Object> applySharedTemplate(CostVariableTemplateApplyRequest request, String operName);

    /**
     * 测试第三方接口连通性。
     */
    Map<String, Object> testRemoteConnection(Map<String, Object> request);

    /**
     * 预览第三方接口数据。
     */
    Map<String, Object> previewRemoteData(Map<String, Object> request);

    /**
     * 刷新第三方变量缓存。
     */
    Map<String, Object> refreshRemoteCache(Long sceneId);
}
