/*
 * Copyright 2016-2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.hbasestore.utils;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.security.visibility.Authorizations;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.gchq.gaffer.hbasestore.HBaseProperties;
import uk.gov.gchq.gaffer.hbasestore.HBaseStore;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.GafferCoprocessor;
import uk.gov.gchq.gaffer.store.StoreException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utilities used in the creation and maintenance of hbase tables.
 */
public final class TableUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TableUtils.class);

    private TableUtils() {
    }

    /**
     * Ensures that the table exists, otherwise it creates it and sets it up to
     * receive Gaffer data
     *
     * @param store the hbase store
     * @throws StoreException if a connection to hbase could not be created or there is a failure to create a table/iterator
     */
    public static void ensureTableExists(final HBaseStore store) throws StoreException {
        final Connection connection = store.getConnection();
        final TableName tableName = store.getProperties().getTableName();
        try {
            final Admin admin = connection.getAdmin();
            if (!admin.tableExists(tableName)) {
                TableUtils.createTable(store);
            }
        } catch (final IOException e) {
            // The method to create a table is synchronised, if you are using the same store only through one client in one JVM you shouldn't get here
            // Someone else got there first, never mind...
        }
    }

    public static Table getTable(final HBaseStore store) throws StoreException {
        final TableName tableName = store.getProperties().getTableName();
        final Connection connection = store.getConnection();
        try {
            return connection.getTable(tableName);
        } catch (IOException e) {
            IOUtils.closeQuietly(connection);
            throw new StoreException(e);
        }
    }

    public static synchronized void createTable(final HBaseStore store)
            throws StoreException {
        final TableName tableName = store.getProperties().getTableName();
        try {
            final Admin admin = store.getConnection().getAdmin();
            if (admin.tableExists(tableName)) {
                LOGGER.info("Table {} exists, not creating", tableName);
                return;
            }
            LOGGER.info("Creating table {}", tableName);

            final HTableDescriptor htable = new HTableDescriptor(tableName);
            final HColumnDescriptor col = new HColumnDescriptor(HBaseStoreConstants.getColFam());
            col.setMaxVersions(Integer.MAX_VALUE);  // TODO: disable versions
            htable.addFamily(col);

            // TODO - need to properly escape commas
            final String schemaJson = Bytes.toString(store.getSchema().toCompactJson()).replaceAll(",", ";;");
            final Map<String, String> options = new HashMap<>(1);
            options.put(HBaseStoreConstants.SCHEMA, schemaJson);

            htable.addCoprocessor(GafferCoprocessor.class.getName(), null, Coprocessor.PRIORITY_USER, options);

            admin.createTable(htable);

//            final String repFactor = store.getProperties().getTableFileReplicationFactor();
//            if (null != repFactor) {
//                LOGGER.info("Table file replication set to {} on table {}", repFactor, tableName);
//                connection.tableOperations().setProperty(tableName, Property.TABLE_FILE_REPLICATION.getKey(), repFactor);
//            }

            // Enable Bloom filters using ElementFunctor
//            LOGGER.info("Enabling Bloom filter on table {}", tableName);
//            connection.tableOperations().setProperty(tableName, Property.TABLE_BLOOM_ENABLED.getKey(), "true");
//            connection.tableOperations().setProperty(tableName, Property.TABLE_BLOOM_KEY_FUNCTOR.getKey(),
//                    store.getKeyPackage().getKeyFunctor().getClass().getName());

            // Remove versioning iterator from table for all scopes
//            LOGGER.info("Removing versioning iterator from table {}", tableName);
//            final EnumSet<IteratorScope> iteratorScopes = EnumSet.allOf(IteratorScope.class);
//            connection.tableOperations().removeIterator(tableName, "vers", iteratorScopes);

        } catch (Throwable e) {
            throw new StoreException(e.getMessage(), e);
        }

        ensureTableExists(store);
        //setLocalityGroups(store);
    }

    /**
     * Creates a connection to an hbase instance using the provided
     * parameters
     *
     * @param zookeepers the zoo keepers
     * @return A connection to an hbase instance
     * @throws StoreException failure to create an hbase connection
     */
    public static Connection getConnection(final String zookeepers) throws StoreException {
        try {
            final Configuration conf = HBaseConfiguration.create();
            conf.set("hbase.zookeeper.quorum", zookeepers);
            return ConnectionFactory.createConnection(conf);
        } catch (IOException e) {
            throw new StoreException(e);
        }
    }

    public static void clearTable(final HBaseStore store, final String... auths) throws StoreException {
        final Connection connection = store.getConnection();
        try {
            if (connection.getAdmin().tableExists(store.getProperties().getTableName())) {
                connection.getAdmin().flush(store.getProperties().getTableName());
                Scan scan = new Scan();
                scan.setAuthorizations(new Authorizations(auths));
                final Table table = connection.getTable(store.getProperties().getTableName());
                ResultScanner scanner = table.getScanner(scan);
                final List<Delete> deletes = new ArrayList<>();
                for (final Result result : scanner) {
                    deletes.add(new Delete(result.getRow()));
                }
                table.delete(deletes);
                connection.getAdmin().flush(store.getProperties().getTableName());
                scanner.close();

                // TODO: work out a better way to clear a table
                scan = new Scan();
                scan.setAuthorizations(new Authorizations(auths));
                scanner = table.getScanner(scan);
                if (scanner.iterator().hasNext()) {
                    LOGGER.debug("The table is not empty! It will be deleted instead.");
                    dropTable(store);
                    createTable(store);
                }
                scanner.close();
            }
        } catch (final IOException e) {
            throw new StoreException(e);
        }
    }

    public static void dropTable(final HBaseStore store) throws StoreException {
        dropTable(store.getConnection(), store.getProperties());
    }

    public static void dropTable(final Connection connection, final HBaseProperties properties) throws StoreException {
        try {
            final Admin admin = connection.getAdmin();
            final TableName tableName = properties.getTableName();
            if (admin.tableExists(tableName)) {
                if (admin.isTableEnabled(tableName)) {
                    admin.disableTable(tableName);
                }
                admin.deleteTable(tableName);
            }
        } catch (final IOException e) {
            throw new StoreException(e);
        }
    }

    public static void dropAllTables(final Connection connection) throws StoreException {
        try {
            final Admin admin = connection.getAdmin();
            for (final TableName tableName : admin.listTableNames()) {
                if (connection.getAdmin().tableExists(tableName)) {
                    if (connection.getAdmin().isTableEnabled(tableName)) {
                        connection.getAdmin().disableTable(tableName);
                    }
                    connection.getAdmin().deleteTable(tableName);
                }
            }
        } catch (final IOException e) {
            throw new StoreException(e);
        }
    }

    //    public static void setLocalityGroups(final HBaseStore store) throws StoreException {
//        final String tableName = store.getProperties().getTableName();
//        Map<String, Set<Text>> localityGroups =
//                new HashMap<>();
//        for (final String entityGroup : store.getSchema().getEntityGroups()) {
//            HashSet<Text> localityGroup = new HashSet<>();
//            localityGroup.add(new Text(entityGroup));
//            localityGroups.put(entityGroup, localityGroup);
//        }
//        for (final String edgeGroup : store.getSchema().getEdgeGroups()) {
//            HashSet<Text> localityGroup = new HashSet<>();
//            localityGroup.add(new Text(edgeGroup));
//            localityGroups.put(edgeGroup, localityGroup);
//        }
//        LOGGER.info("Setting locality groups on table {}", tableName);
//        try {
//            store.getConnection().tableOperations().setLocalityGroups(tableName, localityGroups);
//        } catch (HBaseException | HBaseSecurityException | TableNotFoundException e) {
//            throw new StoreException(e.getMessage(), e);
//        }
//    }

    //    /**
//     * Returns the {@link org.apache.hbase.core.security.Authorizations} of
//     * the current user
//     *
//     * @param connection the connection to an hbase instance
//     * @return The hbase Authorisations of the current user specified in the properties file
//     * @throws StoreException if the table could not be found or other table/security issues
//     */
//    public static Authorizations getCurrentAuthorizations(final Connection connection) throws StoreException {
//        try {
//            return connection.securityOperations().getUserAuthorizations(connection.whoami());
//        } catch (HBaseException | HBaseSecurityException e) {
//            throw new StoreException(e.getMessage(), e);
//        }
//    }

    //    /**
//     * Creates a {@link BatchWriter}
//     * <p>
//     *
//     * @param store the hbase store
//     * @return A new BatchWriter with the settings defined in the
//     * gaffer.hbasestore properties
//     * @throws StoreException if the table could not be found or other table issues
//     */
//    public static BatchWriter createBatchWriter(final HBaseStore store) throws StoreException {
//        return createBatchWriter(store, store.getProperties().getTableName());
//    }


//    /**
//     * Creates a {@link org.apache.hbase.core.client.BatchWriter} for the
//     * specified table
//     * <p>
//     *
//     * @param store     the hbase store
//     * @param tableName the table name
//     * @return A new BatchWriter with the settings defined in the
//     * gaffer.hbasestore properties
//     * @throws StoreException if the table could not be found or other table issues
//     */
//
//    private static BatchWriter createBatchWriter(final HBaseStore store, final String tableName)
//            throws StoreException {
//        final BatchWriterConfig batchConfig = new BatchWriterConfig();
//        batchConfig.setMaxMemory(store.getProperties().getMaxBufferSizeForBatchWriterInBytes());
//        batchConfig.setMaxLatency(store.getProperties().getMaxTimeOutForBatchWriterInMilliseconds(),
//                TimeUnit.MILLISECONDS);
//        batchConfig.setMaxWriteThreads(store.getProperties().getNumThreadsForBatchWriter());
//        try {
//            return store.getConnection().createBatchWriter(tableName, batchConfig);
//        } catch (final TableNotFoundException e) {
//            throw new StoreException("Table not set up! Use table gaffer.hbasestore.utils to create the table"
//                    + store.getProperties().getTableName(), e);
//        }
//    }
}