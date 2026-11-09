package com.novablog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RefreshToken 失效链路端到端验证脚本
 *
 * <p>手工冒烟脚本，需要本地已启动 novablog-server，且 MySQL / Redis 可用。
 * 走真实的 HTTP 接口，不依赖任何测试框架，与 {@link ApiTest} 同款用法。
 *
 * <p>为快速验证「超出宽限窗口判定泄露」那条分支，建议用缩短的窗口启动服务，
 * 并让本脚本等待同样长的时间：
 * <pre>
 *   java -jar novablog-server/target/novablog-server-*.jar --jwt.rotation-grace=3000
 *   GRACE_MS=4000 java -cp novablog-server/target/test-classes com.novablog.RefreshTokenFlowTest
 * </pre>
 * 不设置 GRACE_MS 时默认等待 61 秒，用默认的 60 秒窗口也能跑通，只是慢一些。
 *
 * <p>覆盖三类失败语义，注意它们返回的形态不同：
 * <ul>
 *   <li>刷新失败是业务 401 —— HTTP 200 + body 里 code=401（GlobalExceptionHandler 行为）</li>
 *   <li>受保护接口未认证是真实 HTTP 401 —— 由 Spring Security 过滤器链返回</li>
 * </ul>
 */
public class RefreshTokenFlowTest {

    private static final String BASE_URL =
        System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final long GRACE_MS =
        Long.parseLong(System.getenv().getOrDefault("GRACE_MS", "61000"));

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String username = "rttest" + (System.currentTimeMillis() % 100000000L);

        System.out.println("=== RefreshToken 失效链路验证 ===");
        System.out.println("服务地址: " + BASE_URL + "，宽限窗口等待: " + GRACE_MS + "ms\n");

        // ---- 1. 注册并取得首对令牌 ----
        String regBody = "{\"username\":\"" + username + "\",\"nickname\":\"令牌测试\","
            + "\"password\":\"Test@1234\"}";
        HttpResponse<String> reg = post("/auth/register", regBody, null);
        String at1 = extract(reg.body(), "token");
        String rt1 = extract(reg.body(), "refreshToken");
        check("1. 注册返回令牌对", at1 != null && rt1 != null,
            "code=" + extract(reg.body(), "code"));

        String sidAt = extractSid(at1);
        String sidRt = extractSid(rt1);
        check("2. AT 与 RT 携带同一个 sid", sidAt != null && sidAt.equals(sidRt),
            "at=" + sidAt + ", rt=" + sidRt);

        // ---- 3. 正常轮换 ----
        HttpResponse<String> r1 = refresh(rt1);
        String rt2 = extract(r1.body(), "refreshToken");
        check("3. 每次刷新都签发新 RT", rt2 != null && !rt2.equals(rt1),
            "新 RT 与旧 RT " + (rt2 == null ? "为空" : rt2.equals(rt1) ? "相同" : "不同"));
        check("4. 刷新响应带回 userInfo", extract(r1.body(), "nickname") != null,
            "避免前端刷新后丢失用户信息");
        check("5. 轮换沿用同一 sid", sidAt != null && sidAt.equals(extractSid(rt2)),
            "sid=" + extractSid(rt2));

        // ---- 6. 宽限窗口内旧 RT 重现：幂等收敛，不撤销会话 ----
        HttpResponse<String> r2 = refresh(rt1);
        String rtGrace = extract(r2.body(), "refreshToken");
        check("6. 宽限窗口内旧 RT 重现返回首次轮换的那张 RT",
            rt2 != null && rt2.equals(rtGrace),
            "并发刷新应收敛到同一个令牌，而不是各签发一张");

        // ---- 7. 超出宽限窗口后旧 RT 重现：判定泄露，撤销整个会话 ----
        System.out.println("\n   等待 " + GRACE_MS + "ms 让轮换映射过期...");
        Thread.sleep(GRACE_MS);
        HttpResponse<String> r3 = refresh(rt1);
        check("7. 超窗重放旧 RT 被拒", bizCode(r3) == 401, "code=" + bizCode(r3));

        HttpResponse<String> r4 = refresh(rt2);
        check("8. 判定泄露后同会话的新 RT 一并失效", bizCode(r4) == 401,
            "code=" + bizCode(r4) + "（会话家族已整体撤销）");

        // ---- 9. 登出撤销整个会话 ----
        HttpResponse<String> login = post("/auth/login",
            "{\"username\":\"" + username + "\",\"password\":\"Test@1234\"}", null);
        String at2 = extract(login.body(), "token");
        String rt3 = extract(login.body(), "refreshToken");
        check("9. 重新登录成功", at2 != null && rt3 != null, "code=" + bizCode(login));

        HttpResponse<String> logout = post("/auth/logout",
            "{\"refreshToken\":\"" + rt3 + "\"}", at2);
        check("10. 登出返回成功", bizCode(logout) == 200, "code=" + bizCode(logout));

        HttpResponse<String> r5 = refresh(rt3);
        check("11. 登出后 RT 不能再换发 AT", bizCode(r5) == 401, "code=" + bizCode(r5));

        HttpResponse<String> profile = get("/auth/profile", at2);
        check("12. 登出后 AT 也立即失效", profile.statusCode() == 401,
            "HTTP " + profile.statusCode() + "（会话撤销覆盖 AT）");

        // ---- 13. 畸形令牌不应打成 500 ----
        HttpResponse<String> empty = get("/auth/profile", "");
        check("13. 空令牌返回 401 而非 500", empty.statusCode() == 401,
            "HTTP " + empty.statusCode());
        HttpResponse<String> garbage = get("/auth/profile", "abc.def.ghi");
        check("14. 畸形令牌返回 401 而非 500", garbage.statusCode() == 401,
            "HTTP " + garbage.statusCode());

        System.out.println("\n=== 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========== 断言 ==========

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name + " —— " + detail);
        }
    }

    // ========== HTTP ==========

    private static HttpResponse<String> get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET(), token);
    }

    private static HttpResponse<String> post(String path, String body, String token)
            throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder, String token)
            throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        // POST 且带 body 时才有 Content-Type，GET 不带
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 用 RefreshToken 调刷新接口，RT 放在 Authorization 头里（与前端一致） */
    private static HttpResponse<String> refresh(String refreshToken) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
            .POST(HttpRequest.BodyPublishers.noBody()), refreshToken);
    }

    // ========== 解析 ==========

    /** 业务状态码。后端所有业务异常都是 HTTP 200 + body.code，取不到时返回 -1 */
    private static int bizCode(HttpResponse<String> resp) {
        String code = extract(resp.body(), "code");
        return code == null ? -1 : Integer.parseInt(code);
    }

    private static String extract(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 从 JWT 的 payload 段解出 sid，用于确认 AT 与 RT 属于同一会话 */
    private static String extractSid(String jwt) {
        if (jwt == null) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        Matcher m = Pattern.compile("\"sid\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
        return m.find() ? m.group(1) : null;
    }
}
