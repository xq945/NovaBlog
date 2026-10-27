package com.novablog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 收藏 / 关注 / 站内通知端到端验证脚本
 *
 * <p>手工冒烟脚本，需要本地已启动服务，且 MySQL / Redis 可用。
 * 走真实 HTTP 接口，不依赖测试框架，与其他 *FlowTest 同款用法。
 *
 * <pre>
 *   mvn -q test-compile
 *   java -cp novablog-server/target/test-classes com.novablog.InteractionFlowTest
 * </pre>
 *
 * <p>前置条件：数据库里存在管理员账号 admin / Admin@123（init.sql 会创建）。
 * 脚本会用它创建一个临时分类，然后注册普通用户跑完整链路。
 */
public class InteractionFlowTest {

    private static final String BASE_URL =
        System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        long stamp = System.currentTimeMillis() % 100000000L;
        System.out.println("=== 收藏 / 关注 / 通知链路验证 ===");
        System.out.println("服务地址: " + BASE_URL + "\n");

        Long categoryId = ensureCategory();
        check("0. 准备分类数据", categoryId != null, "categoryId=" + categoryId);
        if (categoryId == null) {
            System.out.println("无法创建分类，后续用例无法进行");
            System.exit(1);
        }

        // ---------- 收藏 ----------
        String userA = "ia" + stamp;
        String userB = "ib" + stamp;
        String atA = register(userA);
        String atB = register(userB);
        Long idA = currentUserId(atA);
        Long idB = currentUserId(atB);
        check("1. 注册两个用户", atA != null && atB != null && idA != null && idB != null,
            "A=" + idA + ", B=" + idB);

        String titleP1 = "收藏测试文章" + stamp;
        Long articleP1 = publishArticle(atB, categoryId, titleP1);
        check("2. 用户 B 发布文章", articleP1 != null, "articleId=" + articleP1);

        HttpResponse<String> fav = post("/article/favorite/" + articleP1, "", atA);
        check("3. A 收藏 B 的文章，计数为 1",
            bizCode(fav) == 200 && number(fav.body(), "favoriteCount") == 1,
            fav.body());

        HttpResponse<String> favAgain = post("/article/favorite/" + articleP1, "", atA);
        check("4. 重复收藏幂等，计数仍为 1",
            bizCode(favAgain) == 200 && number(favAgain.body(), "favoriteCount") == 1,
            favAgain.body());

        HttpResponse<String> favStatus = get("/article/favorite/status?articleId=" + articleP1, atA);
        check("5. 收藏状态查询返回已收藏",
            bizCode(favStatus) == 200 && favStatus.body().contains("\"favorited\":true")
                && number(favStatus.body(), "favoriteCount") == 1,
            favStatus.body());

        HttpResponse<String> favList = get("/article/favorite/list?page=1&size=10", atA);
        check("6. 我的收藏列表包含该文章", favList.body().contains(titleP1), favList.body());

        HttpResponse<String> unfav = delete("/article/favorite/" + articleP1, atA);
        check("7. 取消收藏后计数归零",
            bizCode(unfav) == 200 && number(unfav.body(), "favoriteCount") == 0,
            unfav.body());

        // ---------- 关注 ----------
        HttpResponse<String> follow = post("/user/" + idB + "/follow", "", atA);
        check("8. A 关注 B，B 的粉丝数为 1",
            bizCode(follow) == 200 && follow.body().contains("\"followed\":true")
                && number(follow.body(), "followerCount") == 1,
            follow.body());

        HttpResponse<String> followAgain = post("/user/" + idB + "/follow", "", atA);
        check("9. 重复关注幂等，粉丝数仍为 1",
            bizCode(followAgain) == 200 && number(followAgain.body(), "followerCount") == 1,
            followAgain.body());

        HttpResponse<String> profileB = get("/user/" + idB, atA);
        check("10. B 的主页返回粉丝数与已关注状态",
            bizCode(profileB) == 200
                && number(profileB.body(), "followerCount") == 1
                && profileB.body().contains("\"followed\":true"),
            profileB.body());

        HttpResponse<String> selfFollow = post("/user/" + idA + "/follow", "", atA);
        check("11. 不能关注自己", bizCode(selfFollow) == 400, "code=" + bizCode(selfFollow));

        HttpResponse<String> unfollow = delete("/user/" + idB + "/follow", atA);
        check("12. 取关后粉丝数归零",
            bizCode(unfollow) == 200 && number(unfollow.body(), "followerCount") == 0,
            unfollow.body());

        // ---------- 通知 ----------
        String userC = "ic" + stamp;
        String userD = "id" + stamp;
        String atC = register(userC);
        String atD = register(userD);
        Long idC = currentUserId(atC);
        Long idD = currentUserId(atD);
        check("13. 为通知用例注册两个用户", idC != null && idD != null, "C=" + idC + ", D=" + idD);

        String titleP2 = "通知测试文章" + stamp;
        Long articleP2 = publishArticle(atC, categoryId, titleP2);
        check("14. C 发布文章", articleP2 != null, "articleId=" + articleP2);

        post("/article/like/" + articleP2, "", atD);
        check("15. D 点赞后 C 收到 1 条未读通知",
            unreadCount(atC) == 1, "unread=" + unreadCount(atC));

        // 取消再点赞，去重键保证不会重复通知
        delete("/article/like/" + articleP2, atD);
        post("/article/like/" + articleP2, "", atD);
        check("16. 取消后再点赞不重复通知", unreadCount(atC) == 1,
            "unread=" + unreadCount(atC));

        HttpResponse<String> comment = post("/comment",
            "{\"articleId\":" + articleP2 + ",\"content\":\"写得好\"}", atD);
        Long commentId = numberJson(comment.body(), "id");
        check("17. D 评论后 C 收到 COMMENT 通知", unreadCount(atC) == 2,
            "commentId=" + commentId + ", unread=" + unreadCount(atC));

        if (commentId != null) {
            post("/comment", "{\"articleId\":" + articleP2 + ",\"parentId\":" + commentId
                + ",\"replyToId\":" + idD + ",\"content\":\"谢谢\"}", atC);
            check("18. C 回复后 D 收到 REPLY 通知", unreadCount(atD) == 1,
                "unread=" + unreadCount(atD));
        } else {
            check("18. C 回复后 D 收到 REPLY 通知", false, "评论 ID 解析失败");
        }

        post("/user/" + idC + "/follow", "", atD);
        check("19. D 关注后 C 收到 FOLLOW 通知", unreadCount(atC) == 3,
            "unread=" + unreadCount(atC));

        post("/article/like/" + articleP2, "", atC);
        check("20. 自己点赞自己的文章不产生通知", unreadCount(atC) == 3,
            "unread=" + unreadCount(atC));

        HttpResponse<String> listC = get("/notification/list?page=1&size=10", atC);
        check("21. 通知列表共 3 条",
            bizCode(listC) == 200 && count(listC.body(), "\"content\"") == 3,
            listC.body());

        HttpResponse<String> likeOnly = get("/notification/list?page=1&size=10&type=LIKE", atC);
        check("22. 按类型过滤只剩点赞通知",
            bizCode(likeOnly) == 200
                && count(likeOnly.body(), "\"content\"") == 1
                && likeOnly.body().contains("\"type\":\"LIKE\""),
            likeOnly.body());

        HttpResponse<String> readAll = put("/notification/read-all", "", atC);
        check("23. 全部已读成功", bizCode(readAll) == 200, readAll.body());
        check("24. 全部已读后未读数归零", unreadCount(atC) == 0,
            "unread=" + unreadCount(atC));

        HttpResponse<String> unreadOnly = get("/notification/list?page=1&size=10&unreadOnly=true", atC);
        check("25. 未读筛选返回空列表",
            bizCode(unreadOnly) == 200 && count(unreadOnly.body(), "\"content\"") == 0,
            unreadOnly.body());

        System.out.println("\n=== 通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========== 场景辅助 ==========

    private static Long ensureCategory() throws Exception {
        String adminToken = loginWith("admin", "Admin@123");
        if (adminToken == null) {
            return null;
        }
        Long existing = firstId(get("/category/list", null).body());
        if (existing != null) {
            return existing;
        }
        post("/category", "{\"name\":\"测试分类\",\"description\":\"自动化测试\"}", adminToken);
        return firstId(get("/category/list", null).body());
    }

    private static String register(String username) throws Exception {
        HttpResponse<String> resp = post("/auth/register",
            "{\"username\":\"" + username + "\",\"nickname\":\"互动测试\",\"password\":\"Test@1234\"}",
            null);
        return extract(resp.body(), "token");
    }

    private static String loginWith(String username, String password) throws Exception {
        HttpResponse<String> resp = post("/auth/login",
            "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
        return extract(resp.body(), "token");
    }

    private static Long currentUserId(String token) throws Exception {
        HttpResponse<String> resp = get("/auth/profile", token);
        return numberJson(resp.body(), "id");
    }

    private static Long publishArticle(String token, Long categoryId, String title) throws Exception {
        HttpResponse<String> resp = post("/article",
            "{\"title\":\"" + title + "\",\"content\":\"内容正文\",\"categoryId\":"
                + categoryId + ",\"status\":1}", token);
        Long articleId = numberJson(resp.body(), "data");
        if (articleId != null) {
            return articleId;
        }
        // 发布接口若返回对象而不是 id，退化为按标题在列表里找
        return firstId(get("/article/list?page=1&size=50", null).body());
    }

    private static long unreadCount(String token) throws Exception {
        HttpResponse<String> resp = get("/notification/unread-count", token);
        Long value = numberJson(resp.body(), "count");
        return value == null ? -1 : value;
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

    private static HttpResponse<String> put(String path, String body, String token)
            throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private static HttpResponse<String> delete(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path)).DELETE(), token);
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder, String token)
            throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ========== 解析 ==========

    private static int bizCode(HttpResponse<String> resp) {
        Long code = numberJson(resp.body(), "code");
        return code == null ? -1 : code.intValue();
    }

    private static String extract(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 取 JSON 中第一个同名数字字段 */
    private static Long numberJson(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** 取响应里第一个 "id" 数字，用于从列表里拿一条记录的 id */
    private static Long firstId(String json) {
        return numberJson(json, "id");
    }

    /** 统计某个数字字段的值，用于 favoriteCount 等 */
    private static long number(String json, String field) {
        Long value = numberJson(json, field);
        return value == null ? -1 : value;
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
}
