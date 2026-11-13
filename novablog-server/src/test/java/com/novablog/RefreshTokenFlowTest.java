package com.novablog;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RefreshToken 失效链路端到端验证脚本
 *
 * <p>手工冒烟脚本，需要本地已启动 novablog-server，且 MySQL / Redis 可用。
 * 走真实的 HTTP 接口，不依赖任何测试框架，与 {@link ApiTest} 同款用法。
 *
 * <p>RT 现在通过 HttpOnly Cookie 下发，因此每个「浏览器」用独立的
 * {@link CookieManager} 模拟；重放旧 RT 时改用不带 Cookie 容器的客户端，
 * 显式设置 Cookie 头，否则 CookieManager 会自动带上最新值，重放测试就失效了。
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

    /** 与后端 auth.cookie.refresh-token-name 默认值一致 */
    private static final String RT_COOKIE = "nb_refresh_token";

    /** 模拟一个浏览器：Cookie 容器 + 共享它的 HttpClient */
    private static final class Session {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    /** 不带 Cookie 容器的客户端，用于显式构造「重放旧 RT」的请求 */
    private static final HttpClient RAW_CLIENT = HttpClient.newHttpClient();

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        String username = "rttest" + (System.currentTimeMillis() % 100000000L);

        System.out.println("=== RefreshToken 失效链路验证 ===");
        System.out.println("服务地址: " + BASE_URL + "，宽限窗口等待: " + GRACE_MS + "ms\n");

        // ---- 1. 注册：AT 在响应体，RT 只进 Cookie ----
        Session session = new Session();
        String regBody = "{\"username\":\"" + username + "\",\"nickname\":\"令牌测试\","
            + "\"password\":\"Test@1234\"}";
        HttpResponse<String> reg = post(session, "/auth/register", regBody, null);
        String at1 = extract(reg.body(), "token");
        String rt1 = cookieValue(session, RT_COOKIE);
        String regCookie = rawCookie(reg);
        check("1. 注册返回 AT，RT 通过 Cookie 下发",
            at1 != null && rt1 != null,
            "at=" + (at1 != null) + ", rt=" + (rt1 != null));
        check("2. 响应体不再返回 refreshToken", extract(reg.body(), "refreshToken") == null,
            "RT 不应出现在 JSON 中，避免被前端 JS 读取");
        check("3. Cookie 属性为 HttpOnly + SameSite=Lax + Path=/",
            regCookie != null && regCookie.contains("HttpOnly")
                && regCookie.contains("SameSite=Lax") && regCookie.contains("Path=/"),
            regCookie == null ? "未收到 Set-Cookie" : regCookie);
        check("4. 开发环境 Cookie 不带 Secure", regCookie != null && !regCookie.contains("Secure"),
            "本地 http 下 Secure Cookie 会被浏览器拒收");

        String sidAt = extractSid(at1);
        String sidRt = extractSid(rt1);
        check("5. AT 与 RT 携带同一个 sid", sidAt != null && sidAt.equals(sidRt),
            "at=" + sidAt + ", rt=" + sidRt);

        // ---- 2. 正常轮换 ----
        HttpResponse<String> r1 = refresh(session);
        String rt2 = cookieValue(session, RT_COOKIE);
        check("6. 每次刷新都轮换 Cookie 中的 RT",
            rt2 != null && !rt2.equals(rt1),
            "新 RT 与旧 RT " + (rt2 == null ? "为空" : rt2.equals(rt1) ? "相同" : "不同"));
        check("7. 刷新响应不再返回 refreshToken",
            extract(r1.body(), "refreshToken") == null, "");
        check("8. 刷新响应带回 userInfo", extract(r1.body(), "nickname") != null,
            "避免前端刷新后丢失用户信息");
        check("9. 轮换沿用同一 sid", sidAt != null && sidAt.equals(extractSid(rt2)),
            "sid=" + extractSid(rt2));

        // ---- 3. 宽限窗口内旧 RT 重现：幂等收敛 ----
        HttpResponse<String> r2 = refreshWithRawCookie(rt1);
        String rtGrace = setCookieValue(r2);
        check("10. 宽限窗口内旧 RT 重现返回首次轮换的那张 RT",
            rt2 != null && rt2.equals(rtGrace),
            "并发刷新应收敛到同一个令牌，而不是各签发一张");

        // ---- 4. 超出宽限窗口后旧 RT 重现：判定泄露，撤销整个会话 ----
        System.out.println("\n   等待 " + GRACE_MS + "ms 让轮换映射过期...");
        Thread.sleep(GRACE_MS);
        HttpResponse<String> r3 = refreshWithRawCookie(rt1);
        check("11. 超窗重放旧 RT 被拒", bizCode(r3) == 401, "code=" + bizCode(r3));
        check("12. 超窗重放会清除客户端 Cookie", setCookieValue(r3) != null
                && setCookieValue(r3).isEmpty(),
            "服务端应返回 Max-Age=0 的清 Cookie 指令");

        HttpResponse<String> r4 = refresh(session);
        check("13. 判定泄露后同会话的新 RT 一并失效", bizCode(r4) == 401,
            "code=" + bizCode(r4) + "（会话家族已整体撤销）");

        // ---- 5. 登出撤销整个会话 ----
        Session session2 = new Session();
        HttpResponse<String> login = post(session2, "/auth/login",
            "{\"username\":\"" + username + "\",\"password\":\"Test@1234\"}", null);
        String at2 = extract(login.body(), "token");
        String rt3 = cookieValue(session2, RT_COOKIE);
        check("14. 重新登录成功", at2 != null && rt3 != null,
            "code=" + bizCode(login));

        // body 传空对象而不是完全空 body：接口声明 required=false，
        // 但带上 Content-Type 的空请求体在部分容器上会先触发消息转换失败
        HttpResponse<String> logout = post(session2, "/auth/logout", "{}", at2);
        check("15. 登出返回成功", bizCode(logout) == 200, "code=" + bizCode(logout));
        check("16. 登出后浏览器中的 RT Cookie 被清除",
            cookieValue(session2, RT_COOKIE) == null, "Cookie 应已过期删除");

        HttpResponse<String> r5 = refresh(session2);
        check("17. 登出后 RT 不能再换发 AT", bizCode(r5) == 401, "code=" + bizCode(r5));

        HttpResponse<String> profile = get(session2, "/auth/profile", at2);
        check("18. 登出后 AT 也立即失效", profile.statusCode() == 401,
            "HTTP " + profile.statusCode() + "（会话撤销覆盖 AT）");

        // ---- 6. 过渡期兼容：Authorization 头传 RT 仍可用 ----
        Session session3 = new Session();
        HttpResponse<String> login3 = post(session3, "/auth/login",
            "{\"username\":\"" + username + "\",\"password\":\"Test@1234\"}", null);
        String at3 = extract(login3.body(), "token");
        String rt4 = cookieValue(session3, RT_COOKIE);
        HttpResponse<String> headerRefresh = send(RAW_CLIENT,
            HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody()), rt4);
        check("19. 过渡期仍兼容 Authorization 头传 RT",
            at3 != null && bizCode(headerRefresh) == 200,
            "code=" + bizCode(headerRefresh) + "（auth.cookie.allow-header-fallback）");

        // ---- 7. 畸形令牌不应打成 500 ----
        HttpResponse<String> empty = get(session3, "/auth/profile", "");
        check("20. 空令牌返回 401 而非 500", empty.statusCode() == 401,
            "HTTP " + empty.statusCode());
        HttpResponse<String> garbage = get(session3, "/auth/profile", "abc.def.ghi");
        check("21. 畸形令牌返回 401 而非 500", garbage.statusCode() == 401,
            "HTTP " + garbage.statusCode());
        HttpResponse<String> malformedCookie = send(RAW_CLIENT,
            HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
                .header("Cookie", RT_COOKIE + "=abc.def.ghi")
                .POST(HttpRequest.BodyPublishers.noBody()), null);
        check("22. 畸形 Cookie 值返回 401 而非 500", bizCode(malformedCookie) == 401,
            "code=" + bizCode(malformedCookie));

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

    private static HttpResponse<String> get(Session session, String path, String token)
            throws Exception {
        return send(session.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET(), token);
    }

    private static HttpResponse<String> post(Session session, String path, String body,
                                             String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json");
        builder = body == null
            ? builder.POST(HttpRequest.BodyPublishers.noBody())
            : builder.POST(HttpRequest.BodyPublishers.ofString(body));
        return send(session.client, builder, token);
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder builder,
                                             String token) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 正常刷新：RT 由 CookieManager 自动携带 */
    private static HttpResponse<String> refresh(Session session) throws Exception {
        return send(session.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody()), null);
    }

    /** 重放旧 RT：显式设置 Cookie 头，绕开 CookieManager 的自动更新 */
    private static HttpResponse<String> refreshWithRawCookie(String refreshToken)
            throws Exception {
        return send(RAW_CLIENT,
            HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
                .header("Cookie", RT_COOKIE + "=" + refreshToken)
                .POST(HttpRequest.BodyPublishers.noBody()), null);
    }

    // ========== Cookie 解析 ==========

    /** 从 Cookie 容器取指定名称的当前值 */
    private static String cookieValue(Session session, String name) {
        for (HttpCookie cookie : session.cookies.getCookieStore().getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 取响应中的 Set-Cookie 原文，用于断言属性 */
    private static String rawCookie(HttpResponse<String> resp) {
        List<String> values = resp.headers().allValues("set-cookie");
        return values.stream()
            .filter(v -> v.startsWith(RT_COOKIE + "="))
            .findFirst()
            .orElse(null);
    }

    /** 取响应中 Set-Cookie 里的令牌值（不含属性） */
    private static String setCookieValue(HttpResponse<String> resp) {
        String raw = rawCookie(resp);
        if (raw == null) {
            return null;
        }
        String value = raw.substring(RT_COOKIE.length() + 1);
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon) : value;
    }

    // ========== JSON / JWT 解析 ==========

    /** 业务状态码。后端所有业务异常都是 HTTP 200 + body.code，取不到时返回 -1 */
    private static int bizCode(HttpResponse<String> resp) {
        // code 在 JSON 里是数字而不是字符串，不能用 extract 的字符串模式去匹配
        String code = extractNumber(resp.body(), "code");
        return code == null ? -1 : Integer.parseInt(code);
    }

    private static String extractNumber(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : null;
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
