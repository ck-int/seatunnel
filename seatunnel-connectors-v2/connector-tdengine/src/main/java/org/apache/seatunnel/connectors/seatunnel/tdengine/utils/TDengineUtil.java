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

package org.apache.seatunnel.connectors.seatunnel.tdengine.utils;

import org.apache.seatunnel.connectors.seatunnel.tdengine.constant.TDengineMetaQueryConstant;
import org.apache.seatunnel.connectors.seatunnel.tdengine.exception.TDengineConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.tdengine.exception.TDengineConnectorException;

import lombok.extern.slf4j.Slf4j;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
public class TDengineUtil {

    /**
     * 按 JDBC URL 协议加载并注册对应驱动（REST / WebSocket / Native）。
     *
     * @param jdbcUrl TDengine JDBC URL
     */
    public static synchronized void checkDriverExist(String jdbcUrl) {
        try {
            DriverManager.getDriver(jdbcUrl);
        } catch (SQLException e) {
            log.warn("no available driver found for this {}, waiting for it to load", jdbcUrl);
        }

        String driverName = resolveDriverClassName(jdbcUrl);

        try {
            Class<?> clazz =
                    Class.forName(driverName, true, Thread.currentThread().getContextClassLoader());
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(driver);
        } catch (Exception ex) {
            throw new TDengineConnectorException(
                    TDengineConnectorErrorCode.LOAD_DRIVER_FAILED,
                    "Fail to create driver of class " + driverName,
                    ex);
        }
    }

    /**
     * 根据 JDBC URL 前缀解析驱动类名。
     *
     * @param jdbcUrl TDengine JDBC URL
     * @return 驱动类名
     */
    static String resolveDriverClassName(String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith(TDengineMetaQueryConstant.JDBC_TAOS_RS_PREFIX)) {
            return TDengineMetaQueryConstant.DRIVER_CLASS_RESTFUL;
        }
        if (jdbcUrl != null && jdbcUrl.startsWith(TDengineMetaQueryConstant.JDBC_TAOS_WS_PREFIX)) {
            return TDengineMetaQueryConstant.DRIVER_CLASS_WEBSOCKET;
        }
        return TDengineMetaQueryConstant.DRIVER_CLASS_NATIVE;
    }
}
