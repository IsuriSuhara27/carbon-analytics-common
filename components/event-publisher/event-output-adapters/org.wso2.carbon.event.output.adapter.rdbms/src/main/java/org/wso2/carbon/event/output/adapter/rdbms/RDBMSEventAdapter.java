/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.event.output.adapter.rdbms;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapter;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.core.exception.ConnectionUnavailableException;
import org.wso2.carbon.event.output.adapter.core.exception.OutputEventAdapterException;
import org.wso2.carbon.event.output.adapter.core.exception.OutputEventAdapterRuntimeException;
import org.wso2.carbon.event.output.adapter.core.exception.TestConnectionNotSupportedException;
import org.wso2.carbon.event.output.adapter.rdbms.internal.ExecutionInfo;
import org.wso2.carbon.event.output.adapter.rdbms.internal.ds.RDBMSEventAdapterServiceValueHolder;
import org.wso2.carbon.event.output.adapter.rdbms.internal.util.RDBMSEventAdapterConstants;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.CarbonDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Class will Insert or Update/Insert values to selected RDBMS
 */
public class RDBMSEventAdapter implements OutputEventAdapter {

    private static final Log log = LogFactory.getLog(RDBMSEventAdapter.class);
    private OutputEventAdapterConfiguration eventAdapterConfiguration;
    private Map<String, String> globalProperties;
    private ResourceBundle resourceBundle;
    private Map<String, String> dbTypeMappings;
    private ExecutionInfo executionInfo = null;
    private DataSource dataSource;
    private boolean isUpdate;
    private Queue<Object> events;
    private boolean isBatchInsertionEnabled = false;
    private long timeInterval = 1000;
    private int batchSize = 1000;
    private ExecutorService scheduler;
    private final String IS_BATCH_INSERTION_ENABLED = "isBatchInsertionEnabled";
    private final String BATCH_SIZE = "batchSize";
    private final String TIME_INTERVAL = "timeInterval";
    private String tableName;
    private Lock lock;
    private String executionMode;
    private String updateColumnKeys;

    public RDBMSEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration,
                             Map<String, String> globalProperties) {
        this.eventAdapterConfiguration = eventAdapterConfiguration;
        this.globalProperties = globalProperties;
        this.events = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
    }

    @Override
    public void init() throws OutputEventAdapterException {

        resourceBundle = ResourceBundle
                .getBundle("org.wso2.carbon.event.output.adapter.rdbms.i18n.Resources", Locale.getDefault());
        populateDbMappings();
        if (isBatchInsertionEnabled) {
            scheduler = Executors.newScheduledThreadPool(1);
        }
        tableName = eventAdapterConfiguration.getStaticProperties().get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_TABLE_NAME);
    }

    @Override
    public void testConnect() throws TestConnectionNotSupportedException {

        DataSource dataSource;
        Connection con = null;
        try {
            CarbonDataSource carbonDataSource = RDBMSEventAdapterServiceValueHolder.getDataSourceService().
                    getDataSource(eventAdapterConfiguration.getStaticProperties().
                            get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME));
            if (carbonDataSource != null) {
                dataSource = (DataSource) carbonDataSource.getDSObject();
                con = dataSource.getConnection();
            } else {
                throw new OutputEventAdapterRuntimeException("There is no datasource found by the name "
                        + RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME + " to connect.");
            }
        } catch (Exception e) {
            throw new OutputEventAdapterRuntimeException(e);
        } finally {
            cleanupConnections(null, con);
        }
    }

    @Override
    public void connect() {

        Connection con = null;
        try {
            CarbonDataSource carbonDataSource = RDBMSEventAdapterServiceValueHolder.getDataSourceService()
                    .getDataSource(
                            eventAdapterConfiguration.getStaticProperties().get(RDBMSEventAdapterConstants
                                    .ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME));
            dataSource = (DataSource) carbonDataSource.getDSObject();
            con = ((DataSource) carbonDataSource.getDSObject()).getConnection();
        } catch (DataSourceException e) {
            log.error("No data-source found by the name: " + eventAdapterConfiguration.getStaticProperties()
                    .get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME), e);
            throw new ConnectionUnavailableException(e.getMessage(), e);
        } catch (SQLException e) {
            throw new ConnectionUnavailableException(e);
        } finally {
            cleanupConnections(null, con);
        }
        if (isBatchInsertionEnabled) {
            startScheduler();
        }

        executionMode = eventAdapterConfiguration.getStaticProperties().get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_EXECUTION_MODE);
        updateColumnKeys = eventAdapterConfiguration.getStaticProperties().get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_UPDATE_KEYS);
        Map<String, String> attributesWithTypeMap = extractAttributeTypes(eventAdapterConfiguration.getStaticProperties().
                get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_TABLE_ATTRIBUTES));

        if (attributesWithTypeMap != null) {
            executionInfo = new ExecutionInfo();
            initializeDatabaseExecutionFromDefinition(tableName, executionMode, updateColumnKeys, attributesWithTypeMap);
        }
    }

    @Override
    public void publish(Object message, Map<String, String> dynamicProperties) {
        try {
            if (message instanceof Map) {

                if (executionInfo == null) {
                    executionInfo = new ExecutionInfo();
                    initializeDatabaseExecutionFromMessage(tableName, executionMode, updateColumnKeys, message);
                }
                if (!isBatchInsertionEnabled) {
                    executeProcessActions(message, tableName);
                } else {
                    events.offer(message);
                    if (events.size() >= batchSize) {
                        executeBatchProcessActions((ConcurrentLinkedQueue) events, tableName);
                    }
                }
            } else {
                throw new OutputEventAdapterRuntimeException(
                        message.getClass().toString() + "is not a compatible type. Hence Event is dropped.");
            }
        } catch (OutputEventAdapterException e) {
            log.error(e.getMessage() + " Hence Event is dropped.", e);
        }
    }

    /**
     * Construct all the queries and assign to executionInfo instance
     */
    private void initializeDatabaseExecutionInfo(String tableName, String executionMode, String updateColumnKeys,
                                                 StringBuilder columnTypes, StringBuilder columns,
                                                 StringBuilder valuePositionsBuilder,
                                                 List<Attribute> tableInsertColumnList) {

        //Constructing query to create a new table
        String createTableQuery = constructQuery(tableName, dbTypeMappings.get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_CREATE_TABLE), columnTypes, null, null, null, null);

        //constructing query to insert date into the table row
        String insertTableRowQuery = constructQuery(tableName, dbTypeMappings.get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_INSERT_DATA), null, columns, valuePositionsBuilder, null, null);

        //Constructing query to check for the table existence
        String isTableExistQuery = constructQuery(tableName, dbTypeMappings.get(RDBMSEventAdapterConstants
                .ADAPTER_GENERIC_RDBMS_TABLE_EXIST), null, null, null, null, null);

        executionInfo.setPreparedInsertStatement(insertTableRowQuery);
        executionInfo.setPreparedCreateTableStatement(createTableQuery);
        executionInfo.setInsertQueryColumnOrder(tableInsertColumnList);
        executionInfo.setPreparedTableExistenceCheckStatement(isTableExistQuery);

        if (executionMode.equalsIgnoreCase(
                resourceBundle.getString(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_EXECUTION_MODE_UPDATE))) {

            String[] queryAttributes = updateColumnKeys.trim().split(",");
            List<Attribute> queryAttributeList = new ArrayList<Attribute>(queryAttributes.length);

            for (String queryAttribute : queryAttributes) {

                for (Attribute attribute : executionInfo.getInsertQueryColumnOrder()) {
                    if (queryAttribute.trim().equalsIgnoreCase(attribute.getName())) {
                        queryAttributeList.add(attribute);
                        break;
                    }
                }
            }
            executionInfo.setExistenceCheckQueryColumnOrder(queryAttributeList);

            //Constructing (eg: information = ?  , latitude = ?) type values : columnValues
            StringBuilder columnValues = new StringBuilder("");
            List<Attribute> updateAttributes = new ArrayList<Attribute>();

            boolean appendComma = false;
            for (Attribute at : executionInfo.getInsertQueryColumnOrder()) {
                if (!executionInfo.getExistenceCheckQueryColumnOrder().contains(at)) {
                    if (appendComma) {
                        columnValues.append(" ").append(dbTypeMappings.get(RDBMSEventAdapterConstants
                                .ADAPTER_GENERIC_RDBMS_COMMA)).append(" ");
                    }
                    columnValues.append(at.getName());
                    columnValues.append(" ").append(dbTypeMappings.get(RDBMSEventAdapterConstants
                            .ADAPTER_GENERIC_RDBMS_EQUAL)).append(" ")
                            .append(dbTypeMappings.get(RDBMSEventAdapterConstants
                                    .ADAPTER_GENERIC_RDBMS_QUESTION_MARK)).append(" ");
                    updateAttributes.add(at);
                    appendComma = true;
                }
            }

            //Constructing (eg: id = ?) type values for WHERE condition : condition
            StringBuilder condition = new StringBuilder("");
            boolean appendAnd = false;
            for (Attribute at : executionInfo.getExistenceCheckQueryColumnOrder()) {
                if (appendAnd) {
                    condition.append(" ").append(dbTypeMappings.get(RDBMSEventAdapterConstants
                            .ADAPTER_GENERIC_RDBMS_AND)).append(" ");
                }
                condition.append(at.getName());
                condition.append(" ").append(dbTypeMappings.get(RDBMSEventAdapterConstants
                        .ADAPTER_GENERIC_RDBMS_EQUAL)).append(" ")
                        .append(dbTypeMappings.get(RDBMSEventAdapterConstants
                                .ADAPTER_GENERIC_RDBMS_QUESTION_MARK)).append(" ");
                updateAttributes.add(at);
                appendAnd = true;
            }
            executionInfo.setUpdateQueryColumnOrder(updateAttributes);

            //constructing query to update data into the table
            String tableUpdateRowQuery = constructQuery(tableName, dbTypeMappings.get(RDBMSEventAdapterConstants
                    .ADAPTER_GENERIC_RDBMS_UPDATE_TABLE), null, null, null, columnValues, condition);
            executionInfo.setPreparedUpdateStatement(tableUpdateRowQuery);
        }

    }


    private void initializeDatabaseExecutionFromMessage(String tableName, String executionMode,
                                                        String updateColumnKeys, Object message) {

        if (resourceBundle.getString(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_EXECUTION_MODE_UPDATE)
                .equalsIgnoreCase(executionMode)) {
            isUpdate = true;
            executionInfo.setUpdateMode(true);
        }

        //Constructing (eg: ID  varchar2(255),INFORMATION  varchar2(255)) type values : columnTypes
        StringBuilder columnTypes = new StringBuilder("");

        //Constructing (eg: id,information) type values : columns
        StringBuilder columns = new StringBuilder("");

        //Constructing (eg: ?,?,?) type values : valuePositionsBuilder
        StringBuilder valuePositionsBuilder = new StringBuilder("");

        List<Attribute> tableInsertColumnList = new ArrayList<Attribute>();

        boolean appendComma = false;
        for (Map.Entry<String, Object> entry : (((Map<String, Object>) message).entrySet())) {
            AttributeType type = null;
            String columnName = entry.getKey().toUpperCase();
            if (appendComma) {
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
            }
            columnTypes.append(columnName).append("  ");
            if (entry.getValue() instanceof Integer) {
                type = AttributeType.INT;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_INTEGER));
            } else if (entry.getValue() instanceof Long) {
                type = AttributeType.LONG;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_LONG));
            } else if (entry.getValue() instanceof Float) {
                type = AttributeType.FLOAT;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_FLOAT));
            } else if (entry.getValue() instanceof Double) {
                type = AttributeType.DOUBLE;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DOUBLE));
            } else if (entry.getValue() instanceof String) {
                type = AttributeType.STRING;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_STRING));
            } else if (entry.getValue() instanceof Boolean) {
                type = AttributeType.BOOL;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_BOOLEAN));
            }
            Attribute attribute = new Attribute(entry.getKey(), type);
            if (appendComma) {
                columns.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
                valuePositionsBuilder
                        .append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
            } else {
                appendComma = true;
            }
            tableInsertColumnList.add(attribute);
            columns.append(attribute.getName());
            valuePositionsBuilder
                    .append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_QUESTION_MARK));
        }

        initializeDatabaseExecutionInfo(tableName, executionMode, updateColumnKeys, columnTypes, columns,
                valuePositionsBuilder, tableInsertColumnList);
    }

    private void initializeDatabaseExecutionFromDefinition(String tableName, String executionMode,
                                                           String updateColumnKeys,
                                                           Map<String, String> attributesWithTypeMap) {

        if (resourceBundle.getString(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_EXECUTION_MODE_UPDATE)
                .equalsIgnoreCase(executionMode)) {
            isUpdate = true;
            executionInfo.setUpdateMode(true);
        }

        //Constructing (eg: ID  varchar2(255),INFORMATION  varchar2(255)) type values : columnTypes
        StringBuilder columnTypes = new StringBuilder("");

        //Constructing (eg: id,information) type values : columns
        StringBuilder columns = new StringBuilder("");

        //Constructing (eg: ?,?,?) type values : valuePositionsBuilder
        StringBuilder valuePositionsBuilder = new StringBuilder("");

        List<Attribute> tableInsertColumnList = new ArrayList<Attribute>();

        boolean appendComma = false;
        for (Map.Entry<String, String> entry : ((attributesWithTypeMap).entrySet())) {
            AttributeType type = null;
            String columnName = entry.getKey().toUpperCase();
            if (appendComma) {
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
            }
            columnTypes.append(columnName).append("  ");
            if (entry.getValue().equalsIgnoreCase(AttributeType.INT.toString())) {
                type = AttributeType.INT;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_INTEGER));
            } else if (entry.getValue().equalsIgnoreCase(AttributeType.LONG.toString())) {
                type = AttributeType.LONG;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_LONG));
            } else if (entry.getValue().equalsIgnoreCase(AttributeType.FLOAT.toString())) {
                type = AttributeType.FLOAT;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_FLOAT));
            } else if (entry.getValue().equalsIgnoreCase(AttributeType.DOUBLE.toString())) {
                type = AttributeType.DOUBLE;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DOUBLE));
            } else if (entry.getValue().equalsIgnoreCase(AttributeType.STRING.toString())) {
                type = AttributeType.STRING;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_STRING));
            } else if (entry.getValue().equalsIgnoreCase(AttributeType.BOOL.toString())) {
                type = AttributeType.BOOL;
                columnTypes.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_BOOLEAN));
            }
            Attribute attribute = new Attribute(entry.getKey(), type);
            if (appendComma) {
                columns.append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
                valuePositionsBuilder
                        .append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA));
            } else {
                appendComma = true;
            }
            tableInsertColumnList.add(attribute);
            columns.append(attribute.getName());
            valuePositionsBuilder
                    .append(dbTypeMappings.get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_QUESTION_MARK));
        }

        initializeDatabaseExecutionInfo(tableName, executionMode, updateColumnKeys, columnTypes, columns,
                valuePositionsBuilder, tableInsertColumnList);
    }

    private void executeProcessActions(Object message, String tableName)
            throws OutputEventAdapterException {

        createTableIfNotExist(tableName);
        if (isUpdate) {
            synchronized (this) {
                executeDbActions(message);
            }
        } else {
            executeDbActions(message);
        }
    }

    private void executeBatchProcessActions(ConcurrentLinkedQueue batch, String tableName)
            throws OutputEventAdapterException {

        createTableIfNotExist(tableName);
        try {
            lock.lock();
            if (batch.size() > 0) {
                executeDbActions(batch);
            }
        } finally {
            lock.unlock();
        }
    }

    private void executeDbActions(Object message)
            throws OutputEventAdapterException {

        PreparedStatement stmt = null;
        Connection con = null;

        Map<String, Object> map = (Map<String, Object>) message;
        boolean executeInsert = true;

        try {
            try {
                con = dataSource.getConnection();
                con.setAutoCommit(false);
            } catch (SQLException e) {
                throw new ConnectionUnavailableException(e);
            }

            if (executionInfo.isUpdateMode()) {
                stmt = con.prepareStatement(executionInfo.getPreparedUpdateStatement());
                populateStatement(map, stmt, executionInfo.getUpdateQueryColumnOrder());
                int updatedRows = stmt.executeUpdate();
                con.commit();

                stmt.close();

                if (updatedRows > 0) {
                    executeInsert = false;
                }
            }

            if (executeInsert) {
                stmt = con.prepareStatement(executionInfo.getPreparedInsertStatement());
                populateStatement(map, stmt, executionInfo.getInsertQueryColumnOrder());
                stmt.executeUpdate();
                con.commit();
            }

        } catch (SQLException e) {
            throw new OutputEventAdapterException(
                    "Cannot Execute Insert/Update Query for event " + message.toString() + " " + e.getMessage(), e);
        } finally {
            cleanupConnections(stmt, con);
        }
    }

    private void executeDbActions(ConcurrentLinkedQueue<Object> events) throws OutputEventAdapterException {

        PreparedStatement stmt;
        PreparedStatement updateStmt;
        Connection con;

        try {
            con = dataSource.getConnection();
            con.setAutoCommit(false);
            stmt = con.prepareStatement(executionInfo.getPreparedInsertStatement());

        } catch (SQLException e) {
            throw new ConnectionUnavailableException(e);
        }

        boolean executeInsert = true;
        Object event = new HashMap<>();
        Object message;

        try {
            if (executionInfo.isUpdateMode()) {
                while ((message = events.poll()) != null) {
                    updateStmt = con.prepareStatement(executionInfo.getPreparedUpdateStatement());
                    Map<String, Object> map = (Map<String, Object>) message;
                    event = message;
                    populateStatement(map, updateStmt, executionInfo.getUpdateQueryColumnOrder());
                    int updatedRows = updateStmt.executeUpdate();
                    con.commit();
                    updateStmt.close();
                    if (updatedRows > 0) {
                        executeInsert = false;
                    }

                    if (executeInsert) {
                        populateStatement(map, stmt, executionInfo.getInsertQueryColumnOrder());
                        stmt.addBatch();
                    }
                }
                if (executeInsert && stmt != null) {
                    stmt.executeBatch();
                    con.commit();
                }
            } else {
                while ((message = events.poll()) != null) {
                    Map<String, Object> map = (Map<String, Object>) message;
                    event = message;
                    populateStatement(map, stmt, executionInfo.getInsertQueryColumnOrder());
                    stmt.addBatch();
                }
                if (stmt != null) {
                    stmt.executeBatch();
                    con.commit();
                }
            }

        } catch (SQLException e) {
            throw new OutputEventAdapterException(
                    "Cannot Execute Insert/Update Query for event " + event.toString() + " " + e.getMessage(), e);
        } finally {
            cleanupConnections(stmt, con);
        }
    }

    /**
     * Populating column values to table Insert query
     */
    private void populateStatement(Map<String, Object> map, PreparedStatement stmt, List<Attribute> colOrder)
            throws OutputEventAdapterException {
        Attribute attribute = null;

        try {
            for (int i = 0; i < colOrder.size(); i++) {
                attribute = colOrder.get(i);
                Object value = map.get(attribute.getName());
                if (value != null || attribute.getType() == AttributeType.STRING) {
                    switch (attribute.getType()) {
                        case INT:
                            stmt.setInt(i + 1, (Integer) value);
                            break;
                        case LONG:
                            stmt.setLong(i + 1, (Long) value);
                            break;
                        case FLOAT:
                            stmt.setFloat(i + 1, (Float) value);
                            break;
                        case DOUBLE:
                            stmt.setDouble(i + 1, (Double) value);
                            break;
                        case STRING:
                            stmt.setString(i + 1, (String) value);
                            break;
                        case BOOL:
                            stmt.setBoolean(i + 1, (Boolean) value);
                            break;
                    }
                } else {
                    throw new OutputEventAdapterException("Cannot Execute Insert/Update. Null value detected for " +
                            "attribute : " + attribute.getName() + " ,attribute type : " + attribute.getType());
                }
            }
        } catch (SQLException e) {
            cleanupConnections(stmt, null);
            throw new OutputEventAdapterException("Cannot set value to attribute name " + attribute.getName() + ". " +
                    "Hence dropping the event. " + e.getMessage(), e);
        }
    }

    private void createTableIfNotExist(String tableName)
            throws OutputEventAdapterException {

        if (!executionInfo.isTableExist()) {
            Statement stmt = null;
            Boolean tableExists = true;
            Connection con = null;
            try {
                try {
                    con = dataSource.getConnection();
                    con.setAutoCommit(false);
                } catch (SQLException e) {
                    throw new ConnectionUnavailableException(e);
                }
                stmt = con.createStatement();
                try {
                    stmt.executeQuery(executionInfo.getPreparedTableExistenceCheckStatement());
                    executionInfo.setTableExist(true);

                } catch (SQLException e) {
                    tableExists = false;
                    if (log.isDebugEnabled()) {
                        log.debug("Table " + tableName + " does not Exist. Table Will be created. ");
                    }
                }

                try {
                    if (!tableExists) {
                        stmt.executeUpdate(executionInfo.getPreparedCreateTableStatement());
                        con.commit();
                        executionInfo.setTableExist(true);
                    }
                } catch (SQLException e) {
                    throw new OutputEventAdapterException("Cannot Execute Create Table Query. " + e.getMessage(), e);
                }
            } catch (SQLException e) {
                throw new ConnectionUnavailableException(e);
            } finally {
                cleanupConnections(stmt, con);
            }
        }
    }

    private void cleanupConnections(Statement stmt, Connection connection) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.error("unable to close statement. " + e.getMessage(), e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("unable to close connection. " + e.getMessage(), e);
            }
        }
    }

    /**
     * Replace attribute values with target build queries
     */
    public String constructQuery(String tableName, String query, StringBuilder columnTypes, StringBuilder columns,
                                 StringBuilder values, StringBuilder columnValues, StringBuilder condition) {

        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_TABLE_NAME)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_TABLE_NAME, tableName);
        }
        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMN_TYPES)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMN_TYPES,
                    columnTypes.toString());
        }
        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMNS)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMNS,
                    columns.toString());
        }
        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_VALUES)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_VALUES, values.toString());
        }
        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMN_VALUES)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_COLUMN_VALUES,
                    columnValues.toString());
        }
        if (query.contains(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_CONDITION)) {
            query = query.replace(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_ATTRIBUTE_CONDITION,
                    condition.toString());
        }
        return query;
    }

    /**
     * Populate specific db Mappings
     */
    private void populateDbMappings() throws OutputEventAdapterException {

        String dbName = null;
        dbTypeMappings = new HashMap<String, String>();
        Connection con = null;

        try {
            CarbonDataSource carbonDataSource = RDBMSEventAdapterServiceValueHolder.getDataSourceService()
                    .getDataSource(
                            eventAdapterConfiguration.getStaticProperties().get(RDBMSEventAdapterConstants
                                    .ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME));
            if (carbonDataSource != null) {
                con = ((DataSource) carbonDataSource.getDSObject()).getConnection();
                DatabaseMetaData databaseMetaData = con.getMetaData();
                dbName = databaseMetaData.getDatabaseProductName();
                dbName = dbName.toLowerCase();
                if (dbName.equals(RDBMSEventAdapterConstants.MS_SQL_SERVER_DB_DRIVER)) {
                    dbName = RDBMSEventAdapterConstants.MS_SQL_DB_DRIVER;
                }
            } else {
                throw new OutputEventAdapterException("There is no data-source called " + eventAdapterConfiguration
                        .getStaticProperties().get(RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME));
            }

        } catch (DataSourceException e) {
            log.error(
                    "There is no data-source found by the name: " + eventAdapterConfiguration.getStaticProperties().get(
                            RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DATASOURCE_NAME), e);
            throw new ConnectionUnavailableException(e.getMessage(), e);
        } catch (SQLException e) {
            throw new ConnectionUnavailableException(e);
        } finally {
            cleanupConnections(null, con);
        }

        // Map<String, String> defaultMappings = new HashMap<String, String>();
        String[] staticAttributes = {RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_STRING,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_DOUBLE,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_INTEGER,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_LONG,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_FLOAT,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_BOOLEAN,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_CREATE_TABLE,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_INSERT_DATA,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_TABLE_EXIST,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_UPDATE_TABLE,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_PROPERTY_DATA_TYPE_IN_TABLE,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_PROPERTY_SELECT_FROM_TABLE,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_COMMA,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_QUESTION_MARK,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_EQUAL,
                RDBMSEventAdapterConstants.ADAPTER_GENERIC_RDBMS_AND};

        Boolean staticAttributeExist;
        String attribute = null;
        Map<String, String> defaultMappings = new HashMap<String, String>();

        for (String staticAttribute : staticAttributes) {
            staticAttributeExist = false;
            for (Map.Entry<String, String> entry : globalProperties.entrySet()) {
                attribute = staticAttribute;
                if (staticAttribute.equals(entry.getKey())) {
                    staticAttributeExist = true;
                    defaultMappings.put(entry.getKey(), entry.getValue());
                    break;
                }
            }
            if (!staticAttributeExist) {
                throw new OutputEventAdapterRuntimeException("A mandatory attribute " + attribute + " does not exist");
            }
        }

        Boolean valueExist;

        for (Map.Entry<String, String> defaultMap : defaultMappings.entrySet()) {
            valueExist = false;
            for (Map.Entry<String, String> entry : globalProperties.entrySet()) {
                if (entry.getKey().contains(dbName)) {
                    if (entry.getKey().contains(defaultMap.getKey())) {
                        dbTypeMappings.put(defaultMap.getKey(), entry.getValue());
                        valueExist = true;
                        break;
                    }
                }
            }
            if (!valueExist) {
                dbTypeMappings.put(defaultMap.getKey(), defaultMap.getValue());
            }
        }

        String batchInsertionEnabled = globalProperties.get(IS_BATCH_INSERTION_ENABLED);
        if (batchInsertionEnabled != null && "TRUE".equalsIgnoreCase(batchInsertionEnabled)) {
            isBatchInsertionEnabled = true;
        }

        String timeInterval = globalProperties.get(TIME_INTERVAL);
        if (timeInterval != null) {
            this.timeInterval = Integer.parseInt(timeInterval);
        }

        String batchSize = globalProperties.get(BATCH_SIZE);
        if (batchSize != null) {
            this.batchSize = Integer.parseInt(batchSize);
        }
    }

    @Override
    public void disconnect() {

        if (dataSource != null) {
            dataSource = null;
        }
        if (executionInfo != null) {
            executionInfo.setTableExist(false);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    @Override
    public void destroy() {

        if (executionInfo != null) {
            executionInfo = null;
        }
        if (dataSource != null) {
            dataSource = null;
        }
    }

    @Override
    public boolean isPolled() {
        return false;
    }

    private void startScheduler() {
        final Runnable writer = new Runnable() {
            public void run() {
                if (events.size() > 0) {
                    try {
                        executeBatchProcessActions((ConcurrentLinkedQueue) events, tableName);
                    } catch (OutputEventAdapterException e) {
                        log.error(e.getMessage() + " Hence Event is dropped.", e);
                    }
                }
            }
        };
        ((ScheduledExecutorService) scheduler).scheduleAtFixedRate(writer, 0, timeInterval, MILLISECONDS);
    }

    private Map<String, String> extractAttributeTypes(String attributeDefinition) {
        if (attributeDefinition == null || attributeDefinition.trim().length() == 0) {
            return null;
        }

        String[] entries = attributeDefinition.split(RDBMSEventAdapterConstants.ATTRIBUTE_SEPARATOR);
        String[] keyValue;
        Map<String, String> result = new LinkedHashMap<>();
        for (String attributeWithType : entries) {
            try {
                keyValue = attributeWithType.split(RDBMSEventAdapterConstants.ENTRY_SEPARATOR, 2);
                result.put(keyValue[0].trim(), keyValue[1].trim());
            } catch (Exception e) {
                log.warn("Attribute with type '" + attributeWithType + "' is not defined in the correct format.", e);
            }
        }
        return result;

    }
}
