package com.novablog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 临时 API 测试
 */
public class ApiTest {

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 测试登录
        String loginBody = "{\"username\":\"testuser\",\"password\":\"Test@1234\"}";
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/user/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("登录状态码: " + loginResp.statusCode());
        System.out.println("登录响应: " + loginResp.body());

        // 测试注册
        String regBody = "{\"username\":\"testuser3\",\"nickname\":\"测试3\",\"password\":\"Test@1234\"}";
        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/user/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(regBody))
                .build();

        HttpResponse<String> regResp = client.send(regReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("注册状态码: " + regResp.statusCode());
        System.out.println("注册响应: " + regResp.body());
    }
}
