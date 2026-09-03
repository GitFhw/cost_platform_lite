package com.ruoyi.system.service.cost.engine;

import com.ruoyi.common.utils.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormulaFunctionRegistry {
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Map<String, String> REWRITE_RULES;

    static {
        LinkedHashMap<String, String> rules = new LinkedHashMap<>();
        rules.put("if", "#if.choose(");
        rules.put("max", "#max.pick(");
        rules.put("min", "#min.pick(");
        rules.put("round", "#round.scale(");
        rules.put("between", "#between.hit(");
        rules.put("coalesce", "#coalesce.first(");
        REWRITE_RULES = Collections.unmodifiableMap(rules);
    }

    private FormulaFunctionRegistry() {
    }

    public static String rewrite(String source) {
        if (StringUtils.isEmpty(source)) {
            return source;
        }
        String expression = source.replace("&&", " and ").replace("||", " or ");
        for (Map.Entry<String, String> entry : REWRITE_RULES.entrySet()) {
            expression = expression.replaceAll("\\b" + entry.getKey() + "\\s*\\(", entry.getValue());
        }
        return expression;
    }

    public static Set<String> extractFunctions(String source) {
        LinkedHashSet<String> functions = new LinkedHashSet<>();
        if (StringUtils.isEmpty(source)) {
            return functions;
        }
        Matcher matcher = FUNCTION_PATTERN.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (StringUtils.isNotEmpty(name)) {
                functions.add(name);
            }
        }
        return functions;
    }
}
