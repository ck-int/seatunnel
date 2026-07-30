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

package org.apache.seatunnel.connectors.seatunnel.tdengine.constant;

/**
 * TDengine 元数据相关 SQL 与协议常量。
 *
 * @author ck
 */
public final class TDengineMetaQueryConstant {

    /** 查询服务端版本。 */
    public static final String SERVER_VERSION_SQL = "select server_version()";

    /** TDengine 3.x 按库名与超级表名列子表。 */
    public static final String SUB_TABLE_SQL_V3 =
            "select table_name from information_schema.ins_tables where db_name = '%s' and stable_name='%s'";

    /** TDengine 2.x 按超级表列子表。 */
    public static final String SUB_TABLE_SQL_V2 = "select tbname from %s.%s";

    /** REST JDBC URL 前缀。 */
    public static final String JDBC_TAOS_RS_PREFIX = "jdbc:TAOS-RS://";

    /** WebSocket JDBC URL 前缀。 */
    public static final String JDBC_TAOS_WS_PREFIX = "jdbc:TAOS-WS://";

    /** REST 驱动类名。 */
    public static final String DRIVER_CLASS_RESTFUL = "com.taosdata.jdbc.rs.RestfulDriver";

    /** WebSocket 驱动类名。 */
    public static final String DRIVER_CLASS_WEBSOCKET = "com.taosdata.jdbc.ws.WebSocketDriver";

    /** 原生驱动类名。 */
    public static final String DRIVER_CLASS_NATIVE = "com.taosdata.jdbc.TSDBDriver";

    /** REST SQL 路径。 */
    public static final String REST_SQL_PATH = "/rest/sql";

    /** 2.x 版本号前缀。 */
    public static final String SERVER_VERSION_PREFIX_V2 = "2.";

    /** REST JDBC 结果里字符串列可能包装成的 Jackson JsonNode 类名。 */
    public static final String JACKSON_JSON_NODE_CLASS = "com.fasterxml.jackson.databind.JsonNode";

    /** Jackson JsonNode 取文本的方法名。 */
    public static final String JACKSON_AS_TEXT_METHOD = "asText";

    private TDengineMetaQueryConstant() {}
}
