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

package uk.gov.gchq.gaffer.hbasestore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.security.visibility.CellVisibility;
import org.apache.hadoop.hbase.util.Bytes;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.AddElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.GetAdjacentEntitySeedsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.GetAllElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.GetElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.serialisation.ElementSerialisation;
import uk.gov.gchq.gaffer.hbasestore.utils.HBaseStoreConstants;
import uk.gov.gchq.gaffer.hbasestore.utils.Pair;
import uk.gov.gchq.gaffer.hbasestore.utils.TableUtils;
import uk.gov.gchq.gaffer.operation.Operation;
import uk.gov.gchq.gaffer.operation.data.ElementSeed;
import uk.gov.gchq.gaffer.operation.data.EntitySeed;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAdjacentEntitySeeds;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.StoreTrait;
import uk.gov.gchq.gaffer.store.operation.handler.OperationHandler;
import uk.gov.gchq.gaffer.store.schema.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static uk.gov.gchq.gaffer.store.StoreTrait.ORDERED;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_TRANSFORMATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.PRE_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.QUERY_AGGREGATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.STORE_AGGREGATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.STORE_VALIDATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.TRANSFORMATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.VISIBILITY;

/**
 * An HBase Implementation of the Gaffer Framework
 * <p>
 * The key detail of the HBase implementation is that any Edge inserted by a
 * user is inserted into the hbase table twice, once with the source object
 * being put first in the key and once with the destination bring put first in
 * the key This is to enable an edge to be found in a Range scan when providing
 * only one end of the edge.
 */
public class HBaseStore extends Store {
    public static final Set<StoreTrait> TRAITS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ORDERED, VISIBILITY, PRE_AGGREGATION_FILTERING, POST_AGGREGATION_FILTERING, POST_TRANSFORMATION_FILTERING, TRANSFORMATION, STORE_AGGREGATION, QUERY_AGGREGATION, STORE_VALIDATION)));
    private Connection connection;

    @SuppressFBWarnings(value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", justification = "serialisation is initialised in the initialise method.")
    private ElementSerialisation serialisation;

    @Override
    public void initialise(final Schema schema, final StoreProperties properties)
            throws StoreException {
        super.initialise(schema, properties);
        serialisation = new ElementSerialisation(getSchema());
        TableUtils.ensureTableExists(this);
    }

    /**
     * Creates an HBase {@link Connection}
     * using the properties found in properties file associated with the
     * HBaseStore
     *
     * @return A new {@link Connection}
     * @throws StoreException if there is a failure to connect to hbase.
     */
    public Connection getConnection() throws StoreException {
        if (null == connection || connection.isClosed()) {
            connection = TableUtils.getConnection(getProperties().getZookeepers());
        }
        return connection;
    }

    public void addElements(final Iterable<Element> elements) throws StoreException {
        int batchSize = getProperties().getWriteBufferSize();
        final Table table = TableUtils.getTable(this);
        try {
            final Iterator<Element> itr = elements.iterator();
            while (itr.hasNext()) {
                final List<Put> puts = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize && itr.hasNext(); i++) {
                    final Element element = itr.next();
                    if (null == element) {
                        i--;
                        continue;
                    }
                    final Pair<byte[]> row = serialisation.getRowKeys(element);
                    final byte[] cq = serialisation.buildColumnQualifier(element);
                    final long ts = serialisation.buildTimestamp(element.getProperties());
                    final byte[] value = serialisation.getValue(element);
                    final String visibilityStr = Bytes.toString(serialisation.buildColumnVisibility(element));
                    final CellVisibility visibility = visibilityStr.isEmpty() ? null : new CellVisibility(visibilityStr);
                    final Put put = new Put(row.getFirst());
                    put.addColumn(HBaseStoreConstants.getColFam(), cq, ts, value);
                    if (null != visibility) {
                        put.setCellVisibility(visibility);
                    }
                    puts.add(put);

                    if (null != row.getSecond()) {
                        final Put put2 = new Put(row.getSecond());
                        put2.addColumn(HBaseStoreConstants.getColFam(), cq, value);
                        if (null != visibility) {
                            put2.setCellVisibility(visibility);
                        }
                        puts.add(put2);
                        i++;
                    }
                }
                table.put(puts);
            }
        } catch (IOException e) {
            throw new StoreException(e);
        }
    }

    @Override
    public <OUTPUT> OUTPUT doUnhandledOperation(final Operation<?, OUTPUT> operation, final Context context) {
        throw new UnsupportedOperationException("Operation: " + operation.getClass() + " is not supported");
    }

    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE", justification = "The properties should always be HBaseProperties")
    @Override
    public HBaseProperties getProperties() {
        return (HBaseProperties) super.getProperties();
    }

    @Override
    protected void addAdditionalOperationHandlers() {
        // none
    }

    @Override
    protected OperationHandler<GetElements<ElementSeed, Element>, CloseableIterable<Element>> getGetElementsHandler() {
        return new GetElementsHandler();
    }

    @Override
    protected OperationHandler<GetAllElements<Element>, CloseableIterable<Element>> getGetAllElementsHandler() {
        return new GetAllElementsHandler();
    }

    @Override
    protected OperationHandler<? extends GetAdjacentEntitySeeds, CloseableIterable<EntitySeed>> getAdjacentEntitySeedsHandler() {
        return new GetAdjacentEntitySeedsHandler();
    }

    @Override
    protected OperationHandler<? extends AddElements, Void> getAddElementsHandler() {
        return new AddElementsHandler();
    }

    @Override
    public Set<StoreTrait> getTraits() {
        return TRAITS;
    }

    @Override
    public boolean isValidationRequired() {
        return false;
    }
}