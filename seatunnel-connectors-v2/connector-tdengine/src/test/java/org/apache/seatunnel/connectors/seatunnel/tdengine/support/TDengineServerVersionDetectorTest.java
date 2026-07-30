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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

/**
 * TDengine 服务端版本探测单测。
 *
 * @author ck
 */
@Slf4j
public class TDengineServerVersionDetectorTest {

    @Test
    public void shouldParseMajorVersionFromServerVersionText() {
        assertMajor("2.6.0.34", TDengineServerMajorVersion.V2);
        assertMajor("3.3.5.0", TDengineServerMajorVersion.V3);
        assertMajor("3.0.2.1", TDengineServerMajorVersion.V3);
        assertMajor(null, TDengineServerMajorVersion.V3);
        assertMajor(" ", TDengineServerMajorVersion.V3);
    }

    @Test
    public void shouldBuildRestEndpointFromJdbcUrl() {
        String input = "jdbc:TAOS-RS://127.0.0.1:6041/power";
        String expected = "http://127.0.0.1:6041/rest/sql";
        String actual = TDengineServerVersionDetector.buildRestSqlEndpoint(input);
        log.info("【测试】输入={}, 期望={}, 实际={}", input, expected, actual);
        Assertions.assertEquals(expected, actual);

        String withQuery = "jdbc:TAOS-RS://192.168.1.10:16041/?charset=UTF-8";
        String expectedQuery = "http://192.168.1.10:16041/rest/sql";
        String actualQuery = TDengineServerVersionDetector.buildRestSqlEndpoint(withQuery);
        log.info("【测试】输入={}, 期望={}, 实际={}", withQuery, expectedQuery, actualQuery);
        Assertions.assertEquals(expectedQuery, actualQuery);

        String wsUrl = "jdbc:TAOS-WS://127.0.0.1:16041/power";
        String expectedWs = "http://127.0.0.1:16041/rest/sql";
        String actualWs = TDengineServerVersionDetector.buildRestSqlEndpoint(wsUrl);
        log.info("【测试】输入={}, 期望={}, 实际={}", wsUrl, expectedWs, actualWs);
        Assertions.assertEquals(expectedWs, actualWs);

        String wsMulti = "jdbc:TAOS-WS://127.0.0.1:16041,127.0.0.1:16042/power";
        String expectedMulti = "http://127.0.0.1:16041/rest/sql";
        String actualMulti = TDengineServerVersionDetector.buildRestSqlEndpoint(wsMulti);
        log.info("【测试】输入={}, 期望={}, 实际={}", wsMulti, expectedMulti, actualMulti);
        Assertions.assertEquals(expectedMulti, actualMulti);
    }

    private void assertMajor(String versionText, TDengineServerMajorVersion expected) {
        TDengineServerMajorVersion actual =
                TDengineServerVersionDetector.parseMajorVersion(versionText);
        log.info("【测试】输入={}, 期望={}, 实际={}", versionText, expected, actual);
        Assertions.assertEquals(expected, actual);
    }
}
