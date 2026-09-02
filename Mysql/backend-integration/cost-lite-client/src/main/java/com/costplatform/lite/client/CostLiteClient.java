package com.costplatform.lite.client;

import java.util.Collections;
import java.util.Map;

/**
 * 不依赖 Spring、Servlet 或具体业务框架的轻量计费客户端。
 */
public interface CostLiteClient {
    CostLiteResponse execute(CostLiteRequest request);

    default CostLiteResponse get(String path, CostLiteAuth auth) {
        return get(path, Collections.<String, Object>emptyMap(), auth);
    }

    default CostLiteResponse get(String path, Map<String, ?> query, CostLiteAuth auth) {
        return execute(CostLiteRequest.get(path).query(query).auth(auth).build());
    }

    default CostLiteResponse post(String path, Object body, CostLiteAuth auth) {
        return execute(CostLiteRequest.post(path).body(body).auth(auth).build());
    }

    default CostLiteResponse put(String path, Object body, CostLiteAuth auth) {
        return execute(CostLiteRequest.put(path).body(body).auth(auth).build());
    }

    default CostLiteResponse delete(String path, CostLiteAuth auth) {
        return execute(CostLiteRequest.delete(path).auth(auth).build());
    }
}
