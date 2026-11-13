package com.novablog;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话管理端到端验证脚本
 *
 * <p>手工冒烟脚本，需要本地已启动 novablog-server，且 MySQL / Redis 可用。
 * 走真实 HTTP 接口，不依赖测试框架，与 {@link RefreshTokenFlowTest} 同款用法。
 *
 * <pre>
 *   mvn -q test-compile
 *   java -cp novablog-server/target/test-classes com.novablog.SessionManagementFlowTest
 * </pre>
 *
 * <p>RT 现在通过 HttpOnly Cookie 下发，每台「设备」用一个独立的
 * {@link CookieManager} 模拟，否则多台设备会共用同一张 RT，测不出会话隔离。
 *
 * <p>覆盖：设备列表与当前标记、跨用户越权、单设备下线、退出其他设备、
 * 会话数上限淘汰、改密后其他设备失效。
 *
 * <p>注意两种失败形态：业务校验失败是 HTTP 200 + body.code，
 * 只有安全过滤器链拒绝才是真实 HTTP 401（会话失效时 body.code 为 4011）。
 */
public class SessionManagementFlowTest {

    private static final String BASE_URL =
        System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");

    /**
     * 一台设备：独立的 Cookie 容器 + 共享该容器的 HttpClient。
     * at 只存内存（与前端一致），RT 由 CookieManager 自动管理
     */
    private static final class Device {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String at;
    }

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        long stamp = System.currentTimeMillis() % 100000000L;

        System.out.println("=== 会话管理链路验证 ===");
        System.out.println("服务地址: " + BASE_URL + "\n");

        // ---------- 用户 A：设备列表、越权、单设备下线、退出其他设备 ----------
        String userA = "sessa" + stamp;
        Device a1 = register(userA);
        String sid1 = extractSid(a1.at);
        check("1. 注册返回 AT 并建立会话", a1.at != null && sid1 != null,
            "sid=" + sid1);

        Device a2 = login(userA);
        String sid2 = extractSid(a2.at);
        check("2. 第二次登录产生新的 sid", sid2 != null && !sid2.equals(sid1),
            "sid1=" + sid1 + ", sid2=" + sid2);

        HttpResponse<String> listA = get(a2, "/auth/sessions");
        check("3. 设备列表返回两台设备", count(listA.body(), "\"sid\"") == 2,
            "实际 " + count(listA.body(), "\"sid\"") + " 台");
        check("4. 当前设备被标记且只有一个", matches(listA.body(), "\"current\"\\s*:\\s*true")
            && countMatches(listA.body(), "\"current\"\\s*:\\s*true") == 1,
            "current 标记数量：" + countMatches(listA.body(), "\"current\"\\s*:\\s*true"));

        // 用户 B 拿到 A 的 sid 也不能踢
        String userB = "sessb" + stamp;
        Device b1 = register(userB);
        HttpResponse<String> crossKick = delete(b1, "/auth/sessions/" + sid1);
        check("5. 无法删除其他用户的会话", bizCode(crossKick) == 404,
            "code=" + bizCode(crossKick));

        // 用户 A 踢掉第一台设备
        HttpResponse<String> kick1 = delete(a2, "/auth/sessions/" + sid1);
        check("6. 下线非当前设备返回 current=false",
            bizCode(kick1) == 200 && kick1.body().contains("\"current\":false"),
            kick1.body());

        HttpResponse<String> oldAt = get(a1, "/auth/profile");
        check("7. 被踢设备的 AT 立即失效", oldAt.statusCode() == 401,
            "HTTP " + oldAt.statusCode() + "，code=" + bizCode(oldAt));
        check("8. 失效原因是会话被撤销（4011）而非普通过期", bizCode(oldAt) == 4011,
            "code=" + bizCode(oldAt));

        HttpResponse<String> oldRt = refresh(a1);
        check("9. 被踢设备的 RT 也不能换发新 AT", bizCode(oldRt) == 401,
            "code=" + bizCode(oldRt));

        HttpResponse<String> listA2 = get(a2, "/auth/sessions");
        check("10. 设备列表剩下一台", count(listA2.body(), "\"sid\"") == 1,
            "实际 " + count(listA2.body(), "\"sid\"") + " 台");

        // ---------- 退出其他设备 ----------
        Device a3 = login(userA);
        HttpResponse<String> kickOthers = delete(a3, "/auth/sessions/others");
        check("11. 退出其他设备返回下线数量",
            bizCode(kickOthers) == 200 && matches(kickOthers.body(), "\"count\"\\s*:\\s*1"),
            kickOthers.body());

        HttpResponse<String> kickedAt2 = get(a2, "/auth/profile");
        check("12. 被退出其他设备的会话立即失效", kickedAt2.statusCode() == 401,
            "HTTP " + kickedAt2.statusCode());

        HttpResponse<String> listA3 = get(a3, "/auth/sessions");
        check("13. 只剩当前设备", count(listA3.body(), "\"sid\"") == 1,
            "实际 " + count(listA3.body(), "\"sid\"") + " 台");

        // ---------- 用户 C：会话数上限淘汰 ----------
        String userC = "sessc" + stamp;
        Device firstDevice = null;
        for (int i = 0; i < 6; i++) {
            Device device = loginOrRegister(userC, i);
            if (i == 0) {
                firstDevice = device;
            }
        }
        Device lastDevice = login(userC);
        HttpResponse<String> listC = get(lastDevice, "/auth/sessions");
        check("14. 会话数被限制在上限内（默认 5）", count(listC.body(), "\"sid\"") <= 5,
            "实际 " + count(listC.body(), "\"sid\"") + " 台");

        HttpResponse<String> oldestAt = get(firstDevice, "/auth/profile");
        check("15. 最早登录的会话已被淘汰", oldestAt.statusCode() == 401,
            "HTTP " + oldestAt.statusCode());

        // ---------- 用户 D：改密后其他设备失效 ----------
        String userD = "sessd" + stamp;
        Device d1 = register(userD);
        Device d2 = login(userD);
        HttpResponse<String> changePwd = put(d1, "/user/profile",
            "{\"nickname\":\"改密测试\",\"password\":\"Test@5678\"}");
        check("16. 修改密码成功", bizCode(changePwd) == 200, "code=" + bizCode(changePwd));

        HttpResponse<String> otherDevice = get(d2, "/auth/profile");
        check("17. 改密后其他设备立即失效", otherDevice.statusCode() == 401,
            "HTTP " + otherDevice.statusCode());

        HttpResponse<String> currentDevice = get(d1, "/auth/profile");
        check("18. 发起改密的当前设备仍然有效", currentDevice.statusCode() == 200,
            "HTTP " + currentDevice.statusCode());

        System.out.println("\n=== 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========== 场景辅助 ==========

    private static Device register(String username) throws Exception {
        Device device = new Device();
        HttpResponse<String> resp = post(device, "/auth/register",
            "{\"username\":\"" + username + "\",\"nickname\":\"会话测试\",\"password\":\"Test@1234\"}");
        device.at = extract(resp.body(), "token");
        return device;
    }

    private static Device login(String username) throws Exception {
        Device device = new Device();
        HttpResponse<String> resp = post(device, "/auth/login",
            "{\"username\":\"" + username + "\",\"password\":\"Test@1234\"}");
        device.at = extract(resp.body(), "token");
        return device;
    }

    /** 首次注册，后续登录，用于制造多个会话 */
    private static Device loginOrRegister(String username, int attempt) throws Exception {
        return attempt == 0 ? register(username) : login(username);
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

    private static HttpResponse<String> get(Device device, String path) throws Exception {
        return send(device.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET(), device.at);
    }

    private static HttpResponse<String> post(Device device, String path, String body)
            throws Exception {
        return send(device.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), device.at);
    }

    private static HttpResponse<String> put(Device device, String path, String body)
            throws Exception {
        return send(device.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), device.at);
    }

    private static HttpResponse<String> delete(Device device, String path) throws Exception {
        return send(device.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + path)).DELETE(), device.at);
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder builder,
                                             String token) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 用该设备 Cookie 里的 RT 调刷新接口 */
    private static HttpResponse<String> refresh(Device device) throws Exception {
        return send(device.client,
            HttpRequest.newBuilder(URI.create(BASE_URL + "/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody()), null);
    }

    // ========== 解析 ==========

    private static int bizCode(HttpResponse<String> resp) {
        String code = extractNumber(resp.body(), "code");
        return code == null ? -1 : Integer.parseInt(code);
    }

    private static String extract(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractNumber(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 从 JWT 的 payload 段解出 sid */
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

    private static int count(String text, String needle) {
        if (text == null) {
            return 0;
        }
        int n = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    private static boolean matches(String text, String regex) {
        return text != null && Pattern.compile(regex).matcher(text).find();
    }

    private static int countMatches(String text, String regex) {
        if (text == null) {
            return 0;
        }
        Matcher m = Pattern.compile(regex).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
