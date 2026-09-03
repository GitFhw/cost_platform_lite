package com.ruoyi.system.service.impl.cost;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.vo.CostExpressionAnalysisVo;
import com.ruoyi.system.service.cost.ICostExpressionService;
import com.ruoyi.system.service.cost.engine.FormulaFunctionRegistry;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.Operation;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CostExpressionServiceImpl implements ICostExpressionService {
    private static final int DIVIDE_SCALE = 16;
    private static final String DEFAULT_NAMESPACE_SCOPE = "V,C,I,F,T";
    private static final Pattern NAMESPACE_REFERENCE_PATTERN =
            Pattern.compile("\\b([A-Z])\\.([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern FEE_REFERENCE_PATTERN =
            Pattern.compile("F\\[['\"]([A-Za-z0-9_\\-]+)['\"]\\]");
    private static final Pattern GENERIC_REFERENCE_PATTERN =
            Pattern.compile("\\bV\\.([A-Za-z_][A-Za-z0-9_]*)\\b|\\b([A-Za-z_][A-Za-z0-9_]*)\\b");

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final OperatorOverloader numericOperatorOverloader = new NumericOperatorOverloader();

    @Override
    public void validateExpression(String expression) {
        validateExpression(expression, DEFAULT_NAMESPACE_SCOPE);
    }

    @Override
    public void validateExpression(String expression, String namespaceScope) {
        if (StringUtils.isEmpty(expression)) {
            throw new ServiceException("表达式不能为空");
        }
        try {
            compileExpression(expression);
            CostExpressionAnalysisVo analysis = analyzeExpression(expression, namespaceScope);
            if (!analysis.getDisallowedNamespaces().isEmpty()) {
                throw new ServiceException("表达式引用了未授权命名空间：" + String.join(", ", analysis.getDisallowedNamespaces()));
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("表达式校验失败：" + e.getMessage());
        }
    }

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        try {
            StandardEvaluationContext evaluationContext = new StandardEvaluationContext(buildRootContext(context));
            evaluationContext.addPropertyAccessor(new MapAccessor());
            evaluationContext.setOperatorOverloader(numericOperatorOverloader);
            evaluationContext.setVariable("if", new CommonFunctions());
            evaluationContext.setVariable("max", new MaxFunctions());
            evaluationContext.setVariable("min", new MinFunctions());
            evaluationContext.setVariable("round", new CommonFunctions());
            evaluationContext.setVariable("between", new CommonFunctions());
            evaluationContext.setVariable("coalesce", new CommonFunctions());
            return compileExpression(expression).getValue(evaluationContext);
        } catch (Exception e) {
            throw new ServiceException("表达式执行失败：" + e.getMessage());
        }
    }

    @Override
    public CostExpressionAnalysisVo analyzeExpression(String expression) {
        return analyzeExpression(expression, DEFAULT_NAMESPACE_SCOPE);
    }

    @Override
    public CostExpressionAnalysisVo analyzeExpression(String expression, String namespaceScope) {
        CostExpressionAnalysisVo analysis = new CostExpressionAnalysisVo();
        analysis.setExpression(expression);
        analysis.setRewrittenExpression(FormulaFunctionRegistry.rewrite(expression));

        LinkedHashSet<String> allowedNamespaces = parseNamespaceScope(namespaceScope);
        analysis.setAllowedNamespaces(new ArrayList<>(allowedNamespaces));

        LinkedHashSet<String> namespaceReferences = new LinkedHashSet<>();
        LinkedHashSet<String> disallowedNamespaces = new LinkedHashSet<>();
        LinkedHashSet<String> variableReferences = new LinkedHashSet<>();
        LinkedHashSet<String> feeReferences = new LinkedHashSet<>(extractFeeReferences(expression));

        Matcher namespaceMatcher = NAMESPACE_REFERENCE_PATTERN.matcher(StringUtils.defaultString(expression));
        while (namespaceMatcher.find()) {
            String namespace = namespaceMatcher.group(1);
            String code = namespaceMatcher.group(2);
            if (StringUtils.isEmpty(namespace) || StringUtils.isEmpty(code)) {
                continue;
            }
            namespaceReferences.add(namespace);
            variableReferences.add(namespace + "." + code);
            if (!allowedNamespaces.contains(namespace)) {
                disallowedNamespaces.add(namespace);
            }
        }
        if (!feeReferences.isEmpty()) {
            namespaceReferences.add("F");
            if (!allowedNamespaces.contains("F")) {
                disallowedNamespaces.add("F");
            }
        }

        analysis.setNamespaceReferences(new ArrayList<>(namespaceReferences));
        analysis.setDisallowedNamespaces(new ArrayList<>(disallowedNamespaces));
        analysis.setVariableReferences(new ArrayList<>(variableReferences));
        analysis.setFeeReferences(new ArrayList<>(feeReferences));
        analysis.setFunctionReferences(new ArrayList<>(FormulaFunctionRegistry.extractFunctions(expression)));
        return analysis;
    }

    @Override
    public Set<String> extractReferencedCodes(String expression, Collection<String> candidateCodes) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (StringUtils.isEmpty(expression) || candidateCodes == null || candidateCodes.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String candidate : candidateCodes) {
            if (StringUtils.isNotEmpty(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return result;
        }
        String sanitized = StringUtils.defaultString(expression)
                .replaceAll("'[^']*'", " ")
                .replaceAll("\"[^\"]*\"", " ");
        Matcher matcher = GENERIC_REFERENCE_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            String candidate = firstNonBlank(matcher.group(1), matcher.group(2));
            if (StringUtils.isNotEmpty(candidate) && candidates.contains(candidate)) {
                result.add(candidate);
            }
        }
        for (String feeCode : extractFeeReferences(expression)) {
            if (candidates.contains(feeCode)) {
                result.add(feeCode);
            }
        }
        return result;
    }

    @Override
    public Set<String> extractFeeReferences(String expression) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (StringUtils.isEmpty(expression)) {
            return result;
        }
        Matcher matcher = FEE_REFERENCE_PATTERN.matcher(expression);
        while (matcher.find()) {
            String feeCode = matcher.group(1);
            if (StringUtils.isNotEmpty(feeCode)) {
                result.add(feeCode);
            }
        }
        return result;
    }

    private Map<String, Object> buildRootContext(Map<String, Object> input) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                root.put(entry.getKey(), normalizeContextValue(entry.getValue()));
            }
        }
        root.computeIfAbsent("V", key -> new LinkedHashMap<>());
        root.computeIfAbsent("C", key -> new LinkedHashMap<>());
        root.computeIfAbsent("I", key -> new LinkedHashMap<>());
        root.computeIfAbsent("F", key -> new LinkedHashMap<>());
        root.computeIfAbsent("T", key -> new LinkedHashMap<>());
        return root;
    }

    private Object normalizeContextValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(entry.getKey(), normalizeContextValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                normalized.add(normalizeContextValue(item));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeContextValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        return value;
    }

    private Expression compileExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, key -> parser.parseExpression(rewriteExpression(key)));
    }

    private String rewriteExpression(String source) {
        return normalizeNumericLiterals(FormulaFunctionRegistry.rewrite(source));
    }

    /**
     * 避免 SpEL 对纯整数字面量表达式做整数常量折叠，统一改写为十进制字面量参与计算。
     */
    private String normalizeNumericLiterals(String expression) {
        if (StringUtils.isEmpty(expression)) {
            return expression;
        }
        StringBuilder result = new StringBuilder(expression.length() + 48);
        int length = expression.length();
        int index = 0;
        while (index < length) {
            char current = expression.charAt(index);
            if (current == '\'' || current == '"') {
                index = copyQuotedSegment(expression, index, result, current);
                continue;
            }
            if (!Character.isDigit(current) || !isNumericLiteralStart(expression, index)) {
                result.append(current);
                index++;
                continue;
            }
            int end = findNumericLiteralEnd(expression, index);
            String literal = expression.substring(index, end);
            result.append(literal.contains(".") ? literal : literal + ".0");
            index = end;
        }
        return result.toString();
    }

    private int copyQuotedSegment(String expression, int start, StringBuilder result, char quote) {
        int index = start;
        int length = expression.length();
        result.append(expression.charAt(index));
        index++;
        while (index < length) {
            char current = expression.charAt(index);
            result.append(current);
            index++;
            if (current == '\\' && index < length) {
                result.append(expression.charAt(index));
                index++;
                continue;
            }
            if (current == quote) {
                break;
            }
        }
        return index;
    }

    private boolean isNumericLiteralStart(String expression, int index) {
        if (index <= 0) {
            return true;
        }
        char previous = expression.charAt(index - 1);
        return !Character.isLetterOrDigit(previous) && previous != '_' && previous != '.';
    }

    private int findNumericLiteralEnd(String expression, int start) {
        int index = start;
        int length = expression.length();
        boolean dotSeen = false;
        while (index < length) {
            char current = expression.charAt(index);
            if (Character.isDigit(current)) {
                index++;
                continue;
            }
            if (current == '.' && !dotSeen && index + 1 < length && Character.isDigit(expression.charAt(index + 1))) {
                dotSeen = true;
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private LinkedHashSet<String> parseNamespaceScope(String namespaceScope) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        String raw = StringUtils.defaultIfEmpty(StringUtils.trim(namespaceScope), DEFAULT_NAMESPACE_SCOPE);
        for (String token : raw.split(",")) {
            String normalized = StringUtils.upperCase(StringUtils.trim(token));
            if (StringUtils.isNotEmpty(normalized)) {
                scopes.add(normalized);
            }
        }
        if (scopes.isEmpty()) {
            scopes.addAll(Arrays.asList(DEFAULT_NAMESPACE_SCOPE.split(",")));
        }
        return scopes;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.isNotEmpty(first)) {
            return first;
        }
        return StringUtils.defaultString(second);
    }

    public static class CommonFunctions {
        public Object choose(Object condition, Object trueValue, Object falseValue) {
            return toBoolean(condition) ? trueValue : falseValue;
        }

        public BigDecimal scale(Object value, Object digits) {
            int precision = digits == null ? 2 : toBigDecimal(digits).intValue();
            return toBigDecimal(value).setScale(precision, RoundingMode.HALF_UP);
        }

        public boolean hit(Object value, Object start, Object end) {
            BigDecimal current = toBigDecimal(value);
            return current.compareTo(toBigDecimal(start)) >= 0 && current.compareTo(toBigDecimal(end)) <= 0;
        }

        public Object first(Object first, Object second) {
            return first != null ? first : second;
        }

        public Object first(Object first, Object second, Object third) {
            if (first != null) {
                return first;
            }
            return second != null ? second : third;
        }

        protected boolean toBoolean(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }

        protected BigDecimal toBigDecimal(Object value) {
            if (value == null) {
                return BigDecimal.ZERO;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            }
            return new BigDecimal(String.valueOf(value));
        }
    }

    public static class MaxFunctions extends CommonFunctions {
        public Double pick(Object left, Object right) {
            return Math.max(toBigDecimal(left).doubleValue(), toBigDecimal(right).doubleValue());
        }

        public Double pick(Object first, Object second, Object third) {
            return pick(pick(first, second), third);
        }
    }

    public static class MinFunctions extends CommonFunctions {
        public Double pick(Object left, Object right) {
            return Math.min(toBigDecimal(left).doubleValue(), toBigDecimal(right).doubleValue());
        }

        public Double pick(Object first, Object second, Object third) {
            return pick(pick(first, second), third);
        }
    }

    private static class NumericOperatorOverloader implements OperatorOverloader {
        private static boolean isNumericOperand(Object value) {
            return value instanceof Number || value instanceof BigInteger || value instanceof BigDecimal;
        }

        private static BigDecimal toBigDecimal(Object value) {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            }
            if (value instanceof Number) {
                return new BigDecimal(String.valueOf(value));
            }
            throw new IllegalArgumentException("Unsupported numeric operand: " + value);
        }

        @Override
        public boolean overridesOperation(Operation operation, Object leftOperand, Object rightOperand) {
            return switch (operation) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULUS, POWER ->
                        isNumericOperand(leftOperand) && isNumericOperand(rightOperand);
                default -> false;
            };
        }

        @Override
        public Object operate(Operation operation, Object leftOperand, Object rightOperand) {
            BigDecimal left = toBigDecimal(leftOperand);
            BigDecimal right = toBigDecimal(rightOperand);
            return switch (operation) {
                case ADD -> left.add(right);
                case SUBTRACT -> left.subtract(right);
                case MULTIPLY -> left.multiply(right);
                case DIVIDE -> right.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : left.divide(right, DIVIDE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
                case MODULUS -> right.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : left.remainder(right);
                case POWER -> left.pow(right.intValue());
                default -> throw new IllegalStateException("Unsupported numeric operation: " + operation);
            };
        }
    }
}
