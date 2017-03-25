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
package uk.gov.gchq.gaffer.hbasestore.coprocessor.scanner;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.regionserver.ScannerContext;
import org.apache.hadoop.hbase.util.Bytes;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.ElementDedupeFilterProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.GafferScannerProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.GroupFilterProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.PostAggregationFilterProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.PreAggregationFilterProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.QueryAggregationProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.StoreAggregationProcessor;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.processor.ValidationProcessor;
import uk.gov.gchq.gaffer.hbasestore.serialisation.ElementSerialisation;
import uk.gov.gchq.gaffer.hbasestore.utils.HBaseStoreConstants;
import uk.gov.gchq.gaffer.operation.graph.GraphFilters.DirectedType;
import uk.gov.gchq.gaffer.store.schema.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QueryScanner extends GafferScanner implements RegionScanner {
    public QueryScanner(final RegionScanner scanner,
                        final Scan scan,
                        final Schema schema, final ElementSerialisation serialisation) {
        super(scanner, serialisation,
                createProcessors(getView(scan), schema, serialisation, getDirectedType(scan), getExtraProcessors(scan)));
    }

    private static List<GafferScannerProcessor> createProcessors(
            final View view,
            final Schema schema,
            final ElementSerialisation serialisation,
            final DirectedType directedType,
            final Set<Class<? extends GafferScannerProcessor>> extraProcessors) {
        final List<GafferScannerProcessor> processors = new ArrayList<>();
        if (null != view) {
            processors.add(new GroupFilterProcessor(view));
            if (extraProcessors.remove(ElementDedupeFilterProcessor.class)) {
                processors.add(new ElementDedupeFilterProcessor(view, directedType));
            }
        }

        processors.add(new StoreAggregationProcessor(serialisation, schema));
        processors.add(new ValidationProcessor(schema));

        if (null != view) {
            processors.add(new PreAggregationFilterProcessor(view));
            processors.add(new QueryAggregationProcessor(serialisation, schema, view));
            processors.add(new PostAggregationFilterProcessor(view));
        }

        if (!extraProcessors.isEmpty()) {
            throw new RuntimeException("Unrecognised extra processors: " + extraProcessors);
        }

        return processors;
    }

    private static View getView(final Scan scan) {
        final byte[] viewJson = scan.getAttribute(HBaseStoreConstants.VIEW);
        final View view;
        if (null == viewJson) {
            view = null;
        } else {
            view = View.fromJson(viewJson);
        }
        return view;
    }

    private static DirectedType getDirectedType(final Scan scan) {
        final byte[] directedType = scan.getAttribute(HBaseStoreConstants.DIRECTED_TYPE);
        if (null == directedType) {
            return null;
        }
        return DirectedType.valueOf(Bytes.toString(directedType));
    }

    private static Set<Class<? extends GafferScannerProcessor>> getExtraProcessors(final Scan scan) {
        final byte[] bytes = scan.getAttribute(HBaseStoreConstants.EXTRA_PROCESSORS);
        if (null == bytes) {
            return Collections.emptySet();
        }

        final String[] classNames = Bytes.toString(bytes).split(",");
        final Set<Class<? extends GafferScannerProcessor>> classes = new HashSet<>(classNames.length);
        for (String processorClassName : classNames) {
            try {
                classes.add(Class.forName(processorClassName).asSubclass(GafferScannerProcessor.class));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Invalid gaffer scanner processor: " + processorClassName, e);
            }
        }

        return classes;
    }

    protected RegionScanner getScanner() {
        return (RegionScanner) super.getScanner();
    }

    @Override
    public HRegionInfo getRegionInfo() {
        return getScanner().getRegionInfo();
    }

    @Override
    public boolean isFilterDone() throws IOException {
        return getScanner().isFilterDone();
    }

    @Override
    public boolean reseek(final byte[] row) throws IOException {
        return getScanner().reseek(row);
    }

    @Override
    public long getMaxResultSize() {
        return getScanner().getMaxResultSize();
    }

    @Override
    public long getMvccReadPoint() {
        return getScanner().getMvccReadPoint();
    }

    @Override
    public int getBatch() {
        return getScanner().getBatch();
    }

    @Override
    public boolean nextRaw(final List<Cell> output) throws IOException {
        final List<Cell> input = new ArrayList<>();
        final boolean shouldContinue = getScanner().nextRaw(input);
        _next(input, output);
        return shouldContinue;
    }

    @Override
    public boolean nextRaw(final List<Cell> output, final ScannerContext scannerContext) throws IOException {
        final List<Cell> input = new ArrayList<>();
        final boolean shouldContinue = getScanner().nextRaw(input);
        _next(input, output);
        return shouldContinue;
    }
}