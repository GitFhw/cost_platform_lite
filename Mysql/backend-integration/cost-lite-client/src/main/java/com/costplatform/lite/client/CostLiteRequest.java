package com.costplatform.lite.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 与轻量计费服务交互的通用请求描述。
 */
public final class CostLiteRequest {
    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }

    private final Method method;
    private final String path;
    private final Map<String, Object> queryParameters;
    private final Object body;
    private final CostLiteAuth auth;
    private final Map<String, String> headers;
    private final Boolean retryable;

    private CostLiteRequest(Builder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.queryParameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.queryParameters));
        this.body = builder.body;
        this.auth = builder.auth;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.retryable = builder.retryable;
    }

    public static Builder builder(Method method, String path) {
        return new Builder(method, path);
    }

    public static Builder get(String path) {
        return builder(Method.GET, path);
    }

    public static Builder post(String path) {
        return builder(Method.POST, path);
    }

    public static Builder put(String path) {
        return builder(Method.PUT, path);
    }

    public static Builder delete(String path) {
        return builder(Method.DELETE, path);
    }

    public Method getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, Object> getQueryParameters() {
        return queryParameters;
    }

    public Object getBody() {
        return body;
    }

    public CostLiteAuth getAuth() {
        return auth;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isRetryable() {
        if (retryable != null) {
            return retryable;
        }
        return method != Method.POST;
    }

    public static final class Builder {
        private final Method method;
        private final String path;
        private final Map<String, Object> queryParameters = new LinkedHashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Object body;
        private CostLiteAuth auth = CostLiteAuth.NONE;
        private Boolean retryable;

        private Builder(Method method, String path) {
            if (method == null) {
                throw new IllegalArgumentException("请求方法不能为空");
            }
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("请求路径不能为空");
            }
            this.method = method;
            this.path = path;
        }

        public Builder query(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("查询参数名不能为空");
            }
            queryParameters.put(name, value);
            return this;
        }

        public Builder query(Map<String, ?> parameters) {
            if (parameters != null) {
                for (Map.Entry<String, ?> entry : parameters.entrySet()) {
                    query(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder auth(CostLiteAuth auth) {
            this.auth = auth == null ? CostLiteAuth.NONE : auth;
            return this;
        }

        public Builder header(String name, String value) {
            if (name != null && !name.trim().isEmpty() && value != null) {
                headers.put(name, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    header(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public CostLiteRequest build() {
            return new CostLiteRequest(this);
        }
    }
}
