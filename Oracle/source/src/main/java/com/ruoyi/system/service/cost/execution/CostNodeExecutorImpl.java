package com.ruoyi.system.service.cost.execution;

import com.ruoyi.system.service.cost.execution.match.RuleMatchExecutor;
import com.ruoyi.system.service.cost.execution.model.ExecutionResult;
import com.ruoyi.system.service.cost.execution.model.FeeExecutionResult;
import com.ruoyi.system.service.cost.execution.model.PricingResult;
import com.ruoyi.system.service.cost.execution.model.RuleMatchResult;
import com.ruoyi.system.service.cost.execution.node.*;
import com.ruoyi.system.service.cost.execution.view.FeeExecutionViewAssembler;
import com.ruoyi.system.service.impl.cost.CostRunServiceImpl;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CostNodeExecutorImpl implements CostNodeExecutor {
    private final PricingSupport pricingSupport;
    private final PricingNodeChain pricingNodeChain;
    private final ExecutionAggregationChain executionAggregationChain;
    private final RuleMatchExecutor ruleMatchExecutor;
    private final FeeExecutionViewAssembler feeExecutionViewAssembler;

    public CostNodeExecutorImpl(PricingSupport pricingSupport,
                                PricingNodeChain pricingNodeChain,
                                ExecutionAggregationChain executionAggregationChain,
                                RuleMatchExecutor ruleMatchExecutor,
                                FeeExecutionViewAssembler feeExecutionViewAssembler) {
        this.pricingSupport = pricingSupport;
        this.pricingNodeChain = pricingNodeChain;
        this.executionAggregationChain = executionAggregationChain;
        this.ruleMatchExecutor = ruleMatchExecutor;
        this.feeExecutionViewAssembler = feeExecutionViewAssembler;
    }

    @Override
    public ExecutionResult execute(CostRunServiceImpl.RuntimeSnapshot snapshot,
                                   String taskNo,
                                   String billMonth,
                                   Map<String, Object> input,
                                   String bizNo,
                                   List<CostRunServiceImpl.RuntimeFee> targetFees,
                                   boolean includeExplain,
                                   Map<String, Object> baseContext,
                                   Map<String, Object> variableValues) {
        List<CostRunServiceImpl.RuntimeFee> feesToExecute = targetFees == null || targetFees.isEmpty()
                ? snapshot.fees
                : targetFees;
        List<FeeExecutionResult> feeResults = new ArrayList<>();
        List<Map<String, Object>> timeline = new ArrayList<>();
        ExecutionResult result = new ExecutionResult();

        LinkedHashMap<String, Object> feeResultContext = new LinkedHashMap<>();
        for (CostRunServiceImpl.RuntimeFee fee : feesToExecute) {
            List<CostRunServiceImpl.RuntimeRule> rules = snapshot.rulesByFeeCode.getOrDefault(fee.feeCode, Collections.emptyList());
            RuleMatchResult matchResult = ruleMatchExecutor.matchRule(
                    rules,
                    variableValues,
                    baseContext,
                    snapshot.variablesByCode,
                    includeExplain,
                    pricingSupport);
            if (matchResult == null || matchResult.rule == null) {
                if (includeExplain) {
                    result.skippedFeeExplains.put(fee.feeCode,
                            feeExecutionViewAssembler.buildFeeNoMatchExplain(fee, rules, variableValues,
                                    matchResult == null ? Collections.emptyList() : matchResult.ruleEvaluations));
                }
                timeline.add(feeExecutionViewAssembler.buildSkipStep(fee));
                continue;
            }
            PricingResult pricingResult = calculateAmount(matchResult.rule, matchResult.tier, variableValues, baseContext, feeResultContext, snapshot);
            pricingResult.pricingExplain.put("unitCode", fee.unitCode);
            pricingResult.pricingExplain.put("unitSemantic", feeExecutionViewAssembler.buildUnitSemanticSummary(fee.unitCode));
            FeeExecutionResult feeResult = feeExecutionViewAssembler.buildFeeResult(fee, matchResult, pricingResult, variableValues);
            feeResults.add(feeResult);
            feeResultContext.put(fee.feeCode, feeResult.toExplainView());
            timeline.add(feeExecutionViewAssembler.buildResultStep(feeResult));
        }

        LinkedHashMap<String, Object> resultView = new LinkedHashMap<>();
        resultView.put("taskNo", taskNo);
        resultView.put("sceneCode", snapshot.sceneCode);
        resultView.put("versionNo", snapshot.versionNo);
        resultView.put("snapshotSource", snapshot.snapshotSource);
        resultView.put("bizNo", bizNo);
        resultView.put("feeResults", feeResults.stream().map(FeeExecutionResult::toView).collect(java.util.stream.Collectors.toList()));

        LinkedHashMap<String, Object> explainView = new LinkedHashMap<>();
        explainView.put("timeline", timeline);
        explainView.put("matchedFees", feeResults.stream().map(FeeExecutionResult::toExplainView).collect(java.util.stream.Collectors.toList()));

        result.variableView = variableValues;
        executionAggregationChain.apply(new ExecutionAggregationContext(feeResults, resultView));
        result.resultView = resultView;
        result.explainView = explainView;
        result.feeResults = feeResults;
        return result;
    }

    @Override
    public Map<String, Object> buildFeeNoMatchExplain(CostRunServiceImpl.RuntimeFee fee,
                                                      List<CostRunServiceImpl.RuntimeRule> rules,
                                                      Map<String, Object> variableValues,
                                                      List<Map<String, Object>> ruleEvaluations) {
        return feeExecutionViewAssembler.buildFeeNoMatchExplain(fee, rules, variableValues, ruleEvaluations);
    }

    @Override
    public Map<String, Object> buildFeeFailureExplain(CostRunServiceImpl.RuntimeFee fee, Exception exception) {
        return feeExecutionViewAssembler.buildFeeFailureExplain(fee, exception);
    }


    private PricingResult calculateAmount(CostRunServiceImpl.RuntimeRule rule,
                                          CostRunServiceImpl.RuntimeTier tier,
                                          Map<String, Object> variableValues,
                                          Map<String, Object> baseContext,
                                          Map<String, Object> feeResultContext,
                                          CostRunServiceImpl.RuntimeSnapshot snapshot) {
        PricingResult result = new PricingResult();
        PricingContext context = new PricingContext(rule, tier, snapshot, variableValues, baseContext, feeResultContext, result);
        pricingNodeChain.apply(context, pricingSupport);
        return result;
    }
}
