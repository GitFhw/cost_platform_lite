package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CostExpressionAnalysisVo {
    private String expression;

    private String rewrittenExpression;

    private List<String> allowedNamespaces = new ArrayList<>();

    private List<String> namespaceReferences = new ArrayList<>();

    private List<String> disallowedNamespaces = new ArrayList<>();

    private List<String> variableReferences = new ArrayList<>();

    private List<String> feeReferences = new ArrayList<>();

    private List<String> functionReferences = new ArrayList<>();
}
