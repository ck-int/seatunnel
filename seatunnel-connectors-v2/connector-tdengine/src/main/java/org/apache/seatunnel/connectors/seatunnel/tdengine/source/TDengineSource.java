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

package org.apache.seatunnel.connectors.seatunnel.tdengine.source;

import org.apache.seatunnel.shade.org.apache.commons.lang3.ArrayUtils;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Boundedness;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.source.SourceReader.Context;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.tdengine.config.TDengineSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.tdengine.constant.TDengineMetaQueryConstant;
import org.apache.seatunnel.connectors.seatunnel.tdengine.state.TDengineSourceState;
import org.apache.seatunnel.connectors.seatunnel.tdengine.support.TDengineServerMajorVersion;
import org.apache.seatunnel.connectors.seatunnel.tdengine.support.TDengineServerVersionDetector;
import org.apache.seatunnel.connectors.seatunnel.tdengine.typemapper.TDengineTypeMapper;

import com.taosdata.jdbc.TSDBDriver;
import lombok.Getter;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.apache.seatunnel.connectors.seatunnel.tdengine.config.TDengineSourceConfig.buildSourceConfig;
import static org.apache.seatunnel.connectors.seatunnel.tdengine.utils.TDengineUtil.checkDriverExist;

/**
 * TDengine source each split corresponds one subtable
 *
 * <p>TODO: wait for optimization 1. batch -> batch + stream 2. one item of data writing -> a batch
 * of data writing
 */
public class TDengineSource
        implements SeaTunnelSource<SeaTunnelRow, TDengineSourceSplit, TDengineSourceState> {
    @Getter private final StableMetadata stableMetadata;
    private final TDengineSourceConfig tdengineSourceConfig;
    private final CatalogTable catalogTable;

    @SneakyThrows
    public TDengineSource(ReadonlyConfig pluginConfig) {
        this.tdengineSourceConfig = buildSourceConfig(pluginConfig);
        this.stableMetadata = getStableMetadata(tdengineSourceConfig);
        this.catalogTable =
                CatalogTableUtil.getCatalogTable(
                        tdengineSourceConfig.getStable(), stableMetadata.getRowType());
    }

    @Override
    public String getPluginName() {
        return "TDengine";
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return Collections.singletonList(catalogTable);
    }

    @Override
    public SourceReader<SeaTunnelRow, TDengineSourceSplit> createReader(Context readerContext) {
        return new TDengineSourceReader(tdengineSourceConfig, readerContext);
    }

    @Override
    public SourceSplitEnumerator<TDengineSourceSplit, TDengineSourceState> createEnumerator(
            SourceSplitEnumerator.Context<TDengineSourceSplit> enumeratorContext) {
        return new TDengineSourceSplitEnumerator(
                stableMetadata, tdengineSourceConfig, enumeratorContext);
    }

    @Override
    public SourceSplitEnumerator<TDengineSourceSplit, TDengineSourceState> restoreEnumerator(
            SourceSplitEnumerator.Context<TDengineSourceSplit> enumeratorContext,
            TDengineSourceState checkpointState) {
        return new TDengineSourceSplitEnumerator(
                stableMetadata, tdengineSourceConfig, checkpointState, enumeratorContext);
    }

    private StableMetadata getStableMetadata(TDengineSourceConfig config) throws SQLException {
        String timestampFieldName = null;
        List<String> subTableNames = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        List<SeaTunnelDataType<?>> fieldTypes = new ArrayList<>();

        String jdbcUrl = String.join("", config.getUrl(), config.getDatabase());

        // check td driver whether exist and if not, try to register
        checkDriverExist(jdbcUrl);

        Properties properties = new Properties();
        properties.put(TSDBDriver.PROPERTY_KEY_USER, config.getUsername());
        properties.put(TSDBDriver.PROPERTY_KEY_PASSWORD, config.getPassword());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, properties);
                Statement statement = conn.createStatement()) {
            // 先探测服务端主版本，再读表结构；驱动不匹配时至少能打出版本日志
            TDengineServerMajorVersion majorVersion =
                    TDengineServerVersionDetector.detect(
                            statement, config.getUrl(), config.getUsername(), config.getPassword());
            try (ResultSet metaResultSet =
                    statement.executeQuery(
                            String.format(
                                    "desc %s.%s", config.getDatabase(), config.getStable()))) {
                while (metaResultSet.next()) {
                    if (timestampFieldName == null) {
                        timestampFieldName = metaResultSet.getString(1);
                    }
                    if (config.getReadColumns() != null
                            && !config.getReadColumns().isEmpty()
                            && !config.getReadColumns().contains(metaResultSet.getString(1))) {
                        continue;
                    }
                    fieldNames.add(metaResultSet.getString(1));
                    fieldTypes.add(TDengineTypeMapper.mapping(metaResultSet.getString(2)));
                }
            }
            for (String subTableName : listSubTableNames(statement, config, majorVersion)) {
                if (config.getSubTables() != null
                        && !config.getSubTables().isEmpty()
                        && !config.getSubTables().contains(subTableName)) {
                    continue;
                }
                subTableNames.add(subTableName);
            }
        }

        SeaTunnelRowType rowType = addHiddenAttribute(fieldNames, fieldTypes);
        return new StableMetadata(rowType, timestampFieldName, subTableNames);
    }

    private SeaTunnelRowType addHiddenAttribute(
            List<String> fieldNames, List<SeaTunnelDataType<?>> fieldTypes) {
        // add subtable_name and tags to `seaTunnelRowType`
        // 0-subtable_name / 1-n field_names /
        String[] newFieldNames =
                ArrayUtils.add(fieldNames.toArray(new String[0]), 0, "subtable_name");
        // n+1-> tags
        SeaTunnelDataType<?>[] newFieldTypes =
                ArrayUtils.add(
                        fieldTypes.toArray(new SeaTunnelDataType[0]), 0, BasicType.STRING_TYPE);
        return new SeaTunnelRowType(newFieldNames, newFieldTypes);
    }

    /**
     * 按探测到的服务端主版本列出超级表下的子表。
     *
     * <p>3.x 使用 information_schema.ins_tables；2.x 使用 {@code SELECT TBNAME FROM db.stable}。
     */
    private List<String> listSubTableNames(
            Statement statement,
            TDengineSourceConfig config,
            TDengineServerMajorVersion majorVersion)
            throws SQLException {
        String metaSQL;
        if (majorVersion == TDengineServerMajorVersion.V2) {
            metaSQL =
                    String.format(
                            TDengineMetaQueryConstant.SUB_TABLE_SQL_V2,
                            config.getDatabase(),
                            config.getStable());
        } else {
            metaSQL =
                    String.format(
                            TDengineMetaQueryConstant.SUB_TABLE_SQL_V3,
                            config.getDatabase(),
                            config.getStable());
        }
        try (ResultSet subTableNameResultSet = statement.executeQuery(metaSQL)) {
            return readFirstColumn(subTableNameResultSet);
        }
    }

    private List<String> readFirstColumn(ResultSet resultSet) throws SQLException {
        List<String> values = new ArrayList<>();
        while (resultSet.next()) {
            String value = resultSet.getString(1);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }
}
