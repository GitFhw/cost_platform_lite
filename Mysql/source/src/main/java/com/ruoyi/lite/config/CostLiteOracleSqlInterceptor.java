package com.ruoyi.lite.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 轻量运行端的 Oracle 方言兼容层。
 *
 * <p>母体计费服务的实体、Mapper 和服务逻辑保持不变，MySQL 版本继续使用原始 SQL；
 * 只有检测到当前连接是 Oracle 时，才在 MyBatis 执行前转换母体中保留的少量 MySQL 方言。
 * 这样可以避免为 Oracle 再维护一套平行实体和业务实现。</p>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class CostLiteOracleSqlInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(CostLiteOracleSqlInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Connection connection = findConnection(invocation.getArgs());
        if (connection == null || !isOracle(connection)) {
            return invocation.proceed();
        }

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();
        String oracleSql = rewriteForOracle(originalSql);
        if (!originalSql.equals(oracleSql)) {
            SystemMetaObject.forObject(boundSql).setValue("sql", oracleSql);
            if (log.isDebugEnabled()) {
                log.debug("Converted MySQL-compatible Cost Lite SQL for Oracle: {}", oracleSql);
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(java.util.Properties properties) {
        // 当前转换规则不依赖外部参数，保留标准插件扩展点。
    }

    /**
     * 将一条母体 SQL 转换为 Oracle 12c+ 可执行的 SQL。
     *
     * @param sql 母体 MyBatis 生成的 SQL
     * @return Oracle SQL；无法识别的语句原样返回
     */
    static String rewriteForOracle(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        String rewritten = rewriteMultiRowInsert(sql);
        rewritten = rewriteLimitClauses(rewritten);
        return rewriteFunctions(rewritten);
    }

    private static Connection findConnection(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Connection) {
                return (Connection) arg;
            }
        }
        return null;
    }

    private static boolean isOracle(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("oracle");
        } catch (SQLException ex) {
            log.warn("Unable to detect database product for Cost Lite SQL compatibility", ex);
            return false;
        }
    }

    /**
     * Oracle 不支持 MySQL 的多行 VALUES 写法，改写成 INSERT ALL。
     */
    private static String rewriteMultiRowInsert(String sql) {
        int statementStart = skipWhitespaceAndComments(sql, 0);
        if (!matchesKeyword(sql, statementStart, "insert")) {
            return sql;
        }

        int intoStart = findKeywordOutside(sql, "into", statementStart + 6);
        if (intoStart < 0) {
            return sql;
        }
        int tableStart = skipWhitespaceAndComments(sql, intoStart + 4);
        int columnsStart = findNextOpeningParenthesis(sql, tableStart);
        if (columnsStart < 0) {
            return sql;
        }
        String tableName = sql.substring(tableStart, columnsStart).trim();
        if (tableName.isEmpty() || tableName.indexOf(' ') >= 0) {
            return sql;
        }

        int columnsEnd = findMatchingParenthesis(sql, columnsStart);
        if (columnsEnd < 0) {
            return sql;
        }
        int valuesStart = findKeywordOutside(sql, "values", columnsEnd + 1);
        if (valuesStart < 0) {
            return sql;
        }

        int cursor = skipWhitespaceAndComments(sql, valuesStart + 6);
        List<String> tuples = new ArrayList<>();
        while (cursor < sql.length() && sql.charAt(cursor) == '(') {
            int tupleEnd = findMatchingParenthesis(sql, cursor);
            if (tupleEnd < 0) {
                return sql;
            }
            tuples.add(sql.substring(cursor, tupleEnd + 1).trim());
            cursor = skipWhitespaceAndComments(sql, tupleEnd + 1);
            if (cursor >= sql.length() || sql.charAt(cursor) != ',') {
                break;
            }
            cursor = skipWhitespaceAndComments(sql, cursor + 1);
        }

        if (tuples.size() <= 1) {
            return sql;
        }

        String tail = sql.substring(cursor).trim();
        if (!tail.isEmpty() && !";".equals(tail)) {
            return sql;
        }

        StringBuilder converted = new StringBuilder(sql.length() + tuples.size() * 32);
        converted.append(sql, 0, statementStart);
        converted.append("INSERT ALL");
        String columns = sql.substring(columnsStart, columnsEnd + 1).trim();
        for (String tuple : tuples) {
            converted.append(" INTO ")
                    .append(tableName)
                    .append(' ')
                    .append(columns)
                    .append(" VALUES ")
                    .append(tuple);
        }
        converted.append(" SELECT 1 FROM DUAL");
        if (tail.endsWith(";")) {
            converted.append(';');
        }
        return converted.toString();
    }

    /**
     * 将 MySQL LIMIT 改为 Oracle 12c 的行限制语法。
     */
    private static String rewriteLimitClauses(String sql) {
        StringBuilder converted = new StringBuilder(sql.length() + 32);
        int cursor = 0;
        boolean changed = false;
        while (cursor < sql.length()) {
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor) {
                converted.append(sql, cursor, skipped);
                cursor = skipped;
                continue;
            }

            if (!matchesKeyword(sql, cursor, "limit")) {
                converted.append(sql.charAt(cursor));
                cursor++;
                continue;
            }

            int valueStart = skipWhitespaceAndComments(sql, cursor + 5);
            Token first = readLimitToken(sql, valueStart);
            if (first == null) {
                converted.append(sql, cursor, cursor + 5);
                cursor += 5;
                continue;
            }

            int afterFirst = skipWhitespaceAndComments(sql, first.end);
            String replacement;
            int end;
            if (afterFirst < sql.length() && sql.charAt(afterFirst) == ',') {
                int secondStart = skipWhitespaceAndComments(sql, afterFirst + 1);
                Token second = readLimitToken(sql, secondStart);
                if (second == null) {
                    converted.append(sql, cursor, first.end);
                    cursor = first.end;
                    continue;
                }
                replacement = "OFFSET " + first.text + " ROWS FETCH NEXT " + second.text + " ROWS ONLY";
                end = second.end;
            } else {
                replacement = "FETCH FIRST " + first.text + " ROWS ONLY";
                end = first.end;
            }
            converted.append(replacement);
            cursor = end;
            changed = true;
        }
        return changed ? converted.toString() : sql;
    }

    private static Token readLimitToken(String sql, int start) {
        if (start >= sql.length()) {
            return null;
        }
        if (sql.charAt(start) == '?') {
            return new Token("?", start + 1);
        }
        if (sql.startsWith("#{", start) || sql.startsWith("${", start)) {
            int end = sql.indexOf('}', start + 2);
            if (end >= 0) {
                return new Token(sql.substring(start, end + 1), end + 1);
            }
            return null;
        }
        int cursor = start;
        while (cursor < sql.length()) {
            char current = sql.charAt(cursor);
            if (Character.isDigit(current) || current == ':' || current == '_' || Character.isLetter(current)) {
                cursor++;
                continue;
            }
            break;
        }
        return cursor == start ? null : new Token(sql.substring(start, cursor), cursor);
    }

    /**
     * 改写函数时递归处理括号内容，并跳过字符串、注释和引用标识符。
     */
    private static String rewriteFunctions(String sql) {
        StringBuilder converted = new StringBuilder(sql.length() + 32);
        int cursor = 0;
        while (cursor < sql.length()) {
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor) {
                converted.append(sql, cursor, skipped);
                cursor = skipped;
                continue;
            }

            if (!isIdentifierStart(sql.charAt(cursor))) {
                converted.append(sql.charAt(cursor));
                cursor++;
                continue;
            }

            int nameEnd = readIdentifierEnd(sql, cursor);
            String name = sql.substring(cursor, nameEnd);
            int opening = skipWhitespaceAndComments(sql, nameEnd);
            if (opening >= sql.length() || sql.charAt(opening) != '(' || !isSupportedFunction(name)) {
                converted.append(sql, cursor, nameEnd);
                cursor = nameEnd;
                continue;
            }

            int closing = findMatchingParenthesis(sql, opening);
            if (closing < 0) {
                converted.append(sql, cursor, nameEnd);
                cursor = nameEnd;
                continue;
            }

            String body = rewriteFunctions(sql.substring(opening + 1, closing));
            converted.append(convertFunction(name, body));
            cursor = closing + 1;
        }
        return converted.toString();
    }

    private static String convertFunction(String name, String body) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        switch (normalizedName) {
            case "ifnull":
                return "COALESCE(" + body + ")";
            case "now":
            case "sysdate":
                return body.trim().isEmpty() ? "SYSTIMESTAMP" : name + "(" + body + ")";
            case "concat":
                return convertConcat(body);
            case "group_concat":
                return convertGroupConcat(body);
            case "cast":
                return convertCast(body);
            default:
                return name + "(" + body + ")";
        }
    }

    private static String convertConcat(String body) {
        List<String> arguments = splitTopLevel(body, ',');
        if (arguments.size() <= 1) {
            return arguments.isEmpty() ? "NULL" : arguments.get(0).trim();
        }
        String result = "CONCAT(" + arguments.get(0).trim() + "," + arguments.get(1).trim() + ")";
        for (int i = 2; i < arguments.size(); i++) {
            result = "CONCAT(" + result + "," + arguments.get(i).trim() + ")";
        }
        return result;
    }

    private static String convertGroupConcat(String body) {
        String expression = body.trim();
        if (startsWithKeyword(expression, "distinct")) {
            expression = expression.substring(8).trim();
        }

        int separatorPos = findKeywordOutside(expression, "separator", 0);
        String separator = "','";
        if (separatorPos >= 0) {
            separator = expression.substring(separatorPos + 9).trim();
            expression = expression.substring(0, separatorPos).trim();
        }

        String orderBy = "1";
        int orderPos = findKeywordOutside(expression, "order by", 0);
        if (orderPos >= 0) {
            orderBy = expression.substring(orderPos + 8).trim();
            expression = expression.substring(0, orderPos).trim();
        }
        return "LISTAGG(" + expression + "," + separator + ") WITHIN GROUP (ORDER BY " + orderBy + ")";
    }

    private static String convertCast(String body) {
        int asPos = findKeywordOutside(body, "as", 0);
        if (asPos < 0) {
            return "CAST(" + body + ")";
        }
        String expression = body.substring(0, asPos).trim();
        String type = body.substring(asPos + 2).trim().toLowerCase(Locale.ROOT);
        if (type.equals("char") || type.startsWith("char(") || type.equals("character")) {
            return "TO_CHAR(" + expression + ")";
        }
        return "CAST(" + body + ")";
    }

    private static boolean isSupportedFunction(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        return "ifnull".equals(normalizedName)
                || "now".equals(normalizedName)
                || "sysdate".equals(normalizedName)
                || "concat".equals(normalizedName)
                || "group_concat".equals(normalizedName)
                || "cast".equals(normalizedName);
    }

    private static List<String> splitTopLevel(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        int cursor = 0;
        while (cursor < value.length()) {
            int skipped = skipQuotedOrComment(value, cursor);
            if (skipped > cursor) {
                cursor = skipped;
                continue;
            }
            char current = value.charAt(cursor);
            if (current == '(') {
                depth++;
            } else if (current == ')' && depth > 0) {
                depth--;
            } else if (current == delimiter && depth == 0) {
                parts.add(value.substring(start, cursor));
                start = cursor + 1;
            }
            cursor++;
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static int findNextOpeningParenthesis(String sql, int start) {
        int cursor = start;
        while (cursor < sql.length()) {
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor) {
                cursor = skipped;
                continue;
            }
            if (sql.charAt(cursor) == '(') {
                return cursor;
            }
            if (sql.charAt(cursor) == ';') {
                return -1;
            }
            cursor++;
        }
        return -1;
    }

    private static int findMatchingParenthesis(String sql, int opening) {
        int depth = 0;
        int cursor = opening;
        while (cursor < sql.length()) {
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor) {
                cursor = skipped;
                continue;
            }
            char current = sql.charAt(cursor);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return cursor;
                }
            }
            cursor++;
        }
        return -1;
    }

    private static int findKeywordOutside(String sql, String keyword, int start) {
        int cursor = Math.max(start, 0);
        int depth = 0;
        while (cursor < sql.length()) {
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor) {
                cursor = skipped;
                continue;
            }
            char current = sql.charAt(cursor);
            if (current == '(') {
                depth++;
                cursor++;
                continue;
            }
            if (current == ')' && depth > 0) {
                depth--;
                cursor++;
                continue;
            }
            if (depth == 0 && matchesKeyword(sql, cursor, keyword)) {
                return cursor;
            }
            cursor++;
        }
        return -1;
    }

    private static boolean startsWithKeyword(String value, String keyword) {
        return matchesKeyword(value, 0, keyword);
    }

    private static boolean matchesKeyword(String value, int start, String keyword) {
        if (start < 0 || start + keyword.length() > value.length()) {
            return false;
        }
        if (!value.regionMatches(true, start, keyword, 0, keyword.length())) {
            return false;
        }
        int before = start - 1;
        int after = start + keyword.length();
        return (before < 0 || !isIdentifierPart(value.charAt(before)))
                && (after >= value.length() || !isIdentifierPart(value.charAt(after)));
    }

    private static int skipWhitespaceAndComments(String sql, int start) {
        int cursor = Math.max(start, 0);
        while (cursor < sql.length()) {
            if (Character.isWhitespace(sql.charAt(cursor))) {
                cursor++;
                continue;
            }
            int skipped = skipQuotedOrComment(sql, cursor);
            if (skipped > cursor && isCommentStart(sql, cursor)) {
                cursor = skipped;
                continue;
            }
            break;
        }
        return cursor;
    }

    private static int skipQuotedOrComment(String sql, int start) {
        if (start >= sql.length()) {
            return start;
        }
        char current = sql.charAt(start);
        if (current == '\'' || current == '"' || current == '`') {
            char quote = current;
            int cursor = start + 1;
            while (cursor < sql.length()) {
                if (sql.charAt(cursor) == quote) {
                    if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == quote) {
                        cursor += 2;
                        continue;
                    }
                    return cursor + 1;
                }
                if (sql.charAt(cursor) == '\\' && quote == '\'' && cursor + 1 < sql.length()) {
                    cursor += 2;
                } else {
                    cursor++;
                }
            }
            return sql.length();
        }
        if (current == '#') {
            int newline = sql.indexOf('\n', start + 1);
            return newline < 0 ? sql.length() : newline + 1;
        }
        if (current == '-' && start + 1 < sql.length() && sql.charAt(start + 1) == '-') {
            int newline = sql.indexOf('\n', start + 2);
            return newline < 0 ? sql.length() : newline + 1;
        }
        if (current == '/' && start + 1 < sql.length() && sql.charAt(start + 1) == '*') {
            int end = sql.indexOf("*/", start + 2);
            return end < 0 ? sql.length() : end + 2;
        }
        return start;
    }

    private static boolean isCommentStart(String sql, int start) {
        return start < sql.length() && (sql.charAt(start) == '#'
                || (sql.charAt(start) == '-' && start + 1 < sql.length() && sql.charAt(start + 1) == '-')
                || (sql.charAt(start) == '/' && start + 1 < sql.length() && sql.charAt(start + 1) == '*'));
    }

    private static int readIdentifierEnd(String sql, int start) {
        int cursor = start + 1;
        while (cursor < sql.length() && isIdentifierPart(sql.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static final class Token {
        private final String text;
        private final int end;

        private Token(String text, int end) {
            this.text = text;
            this.end = end;
        }
    }
}
