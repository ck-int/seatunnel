/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.tdengine.support;

import org.apache.commons.lang3.StringUtils;

import org.apache.seatunnel.connectors.seatunnel.tdengine.constant.TDengineMetaQueryConstant;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动探测 TDengine 服务端主版本。
 *
 * <p>优先走 JDBC {@code select server_version()}；当驱动与服务端不匹配导致结果为 null 时，再通过 REST HTTP 探测（2.x/3.x REST
 * 均支持该 SQL）。
 *
 * @author ck
 */
@Slf4j
public final class TDengineServerVersionDetector {

    private static final Pattern VERSION_IN_DATA_PATTERN =
            Pattern.compile("\"data\"\\s*:\\s*\\[\\[\\s*\"([^\"]+)\"");

    private static final int HTTP_CONNECT_TIMEOUT_MS = 5000;

    private static final int HTTP_READ_TIMEOUT_MS = 10000;

    private static final int HTTP_OK = 200;

    private TDengineServerVersionDetector() {}

    /**
     * 探测服务端主版本。
     *
     * @param statement 已打开的 JDBC Statement
     * @param jdbcUrl 原始 JDBC URL（用于 REST 回退，可含或不含 database）
     * @param username 用户名
     * @param password 密码
     * @return 主版本；无法识别时默认按 3.x 处理
     */
    public static TDengineServerMajorVersion detect(
            Statement statement, String jdbcUrl, String username, String password) {
        String versionText = queryVersionByJdbc(statement);
        if (StringUtils.isBlank(versionText)) {
            versionText = queryVersionByRest(jdbcUrl, username, password);
        }
        TDengineServerMajorVersion majorVersion = parseMajorVersion(versionText);
        log.info("Detected TDengine server version text={}, major={}", versionText, majorVersion);
        return majorVersion;
    }

    /**
     * 将版本字符串解析为主版本。
     *
     * @param versionText 例如 2.6.0.34 / 3.3.5.0
     * @return 主版本；空白或无法识别时返回 {@link TDengineServerMajorVersion#V3}
     */
    public static TDengineServerMajorVersion parseMajorVersion(String versionText) {
        if (StringUtils.isBlank(versionText)) {
            return TDengineServerMajorVersion.V3;
        }
        String trimmed = versionText.trim();
        if (trimmed.startsWith(TDengineMetaQueryConstant.SERVER_VERSION_PREFIX_V2)) {
            return TDengineServerMajorVersion.V2;
        }
        return TDengineServerMajorVersion.V3;
    }

    private static String queryVersionByJdbc(Statement statement) {
        try (ResultSet resultSet =
                statement.executeQuery(TDengineMetaQueryConstant.SERVER_VERSION_SQL)) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (Exception e) {
            log.warn("Query server_version() by JDBC failed: {}", e.getMessage());
        }
        return null;
    }

    private static String queryVersionByRest(String jdbcUrl, String username, String password) {
        String restEndpoint = buildRestSqlEndpoint(jdbcUrl);
        if (StringUtils.isBlank(restEndpoint)) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(restEndpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            if (StringUtils.isNotBlank(username)) {
                String rawPassword = password == null ? "" : password;
                String token =
                        Base64.getEncoder()
                                .encodeToString(
                                        (username + ":" + rawPassword)
                                                .getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + token);
            }
            byte[] body =
                    TDengineMetaQueryConstant.SERVER_VERSION_SQL.getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            int status = connection.getResponseCode();
            if (status != HTTP_OK) {
                log.warn("REST server_version() http status={}", status);
                return null;
            }
            String responseBody = readResponseBody(connection);
            Matcher matcher = VERSION_IN_DATA_PATTERN.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
            log.warn("REST server_version() response has no version field: {}", responseBody);
        } catch (Exception e) {
            log.warn("Query server_version() by REST failed: {}", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    static String buildRestSqlEndpoint(String jdbcUrl) {
        String hostPortAndPath = extractAdapterHostPortAndPath(jdbcUrl);
        if (StringUtils.isBlank(hostPortAndPath)) {
            return null;
        }
        int slashIdx = hostPortAndPath.indexOf('/');
        String hostPort = slashIdx >= 0 ? hostPortAndPath.substring(0, slashIdx) : hostPortAndPath;
        int questionIdx = hostPort.indexOf('?');
        if (questionIdx >= 0) {
            hostPort = hostPort.substring(0, questionIdx);
        }
        // WebSocket 支持多端点 host1:6041,host2:6041，REST 回退只取第一个
        int commaIdx = hostPort.indexOf(',');
        if (commaIdx >= 0) {
            hostPort = hostPort.substring(0, commaIdx);
        }
        if (StringUtils.isBlank(hostPort)) {
            return null;
        }
        return "http://" + hostPort + TDengineMetaQueryConstant.REST_SQL_PATH;
    }

    /**
     * 从 REST / WebSocket JDBC URL 中截取 host:port 及后续路径。
     *
     * @param jdbcUrl JDBC URL
     * @return host:port[/db]...；非适配器协议返回 null
     */
    private static String extractAdapterHostPortAndPath(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return null;
        }
        if (jdbcUrl.startsWith(TDengineMetaQueryConstant.JDBC_TAOS_RS_PREFIX)) {
            return jdbcUrl.substring(TDengineMetaQueryConstant.JDBC_TAOS_RS_PREFIX.length());
        }
        if (jdbcUrl.startsWith(TDengineMetaQueryConstant.JDBC_TAOS_WS_PREFIX)) {
            return jdbcUrl.substring(TDengineMetaQueryConstant.JDBC_TAOS_WS_PREFIX.length());
        }
        return null;
    }

    private static String readResponseBody(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
