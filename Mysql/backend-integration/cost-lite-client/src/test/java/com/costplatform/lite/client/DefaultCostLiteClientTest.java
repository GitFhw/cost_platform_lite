package com.costplatform.lite.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultCostLiteClientTest {
    private HttpServer server;
    private CostLiteClientProperties properties;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        properties = new CostLiteClientProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAdminToken("management-secret");
        properties.setMaxResponseBytes(1024 * 1024);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void sendsManagementTokenAndParsesResponse() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/echo", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(read(exchange.getRequestBody()));
            assertEquals("management-secret", exchange.getRequestHeaders().getFirst("X-Cost-Lite-Token"));
            write(exchange, 200, "{\"code\":200,\"msg\":\"ok\",\"data\":{\"value\":1}}");
        });
        server.start();

        CostLiteResponse response = new DefaultCostLiteClient(properties).execute(
                CostLiteRequest.post("/echo")
                        .query("keyword", "a b")
                        .body(Collections.singletonMap("name", "demo"))
                        .auth(CostLiteAuth.MANAGEMENT)
                        .build());

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getMessage());
        assertEquals(1, response.getBodyNode().path("data").path("value").asInt());
        assertEquals("keyword=a+b", query.get());
        assertTrue(body.get().contains("\"name\":\"demo\""));
    }

    @Test
    public void retriesIdempotentRequestOnServerError() {
        AtomicInteger count = new AtomicInteger();
        server.createContext("/retry", exchange -> {
            if (count.incrementAndGet() == 1) {
                write(exchange, 500, "{\"code\":500,\"msg\":\"busy\"}");
            } else {
                write(exchange, 200, "{\"code\":200,\"data\":{\"ok\":true}}");
            }
        });
        properties.setMaxRetries(1);
        properties.setRetryBackoffMillis(0);
        server.start();

        CostLiteResponse response = new DefaultCostLiteClient(properties).get("/retry", CostLiteAuth.NONE);

        assertTrue(response.isSuccess());
        assertEquals(2, count.get());
    }

    @Test
    public void exposesUpstreamErrorDetails() {
        server.createContext("/failure", exchange -> write(
                exchange, 200, "{\"code\":400,\"msg\":\"参数错误\"}"));
        server.start();

        try {
            new DefaultCostLiteClient(properties).get("/failure", CostLiteAuth.NONE);
        } catch (CostLiteClientException exception) {
            assertEquals(200, exception.getHttpStatus());
            assertEquals(Integer.valueOf(400), exception.getUpstreamCode());
            assertEquals("参数错误", exception.getMessage());
            return;
        }
        throw new AssertionError("应当抛出 CostLiteClientException");
    }

    private String read(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        StringBuilder result = new StringBuilder();
        int count;
        while ((count = input.read(buffer)) >= 0) {
            result.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private void write(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
