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

package uk.gov.gchq.gaffer.hbasestore.operation.handler;

import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.hbasestore.HBaseStore;
import uk.gov.gchq.gaffer.hbasestore.filter.ElementDeduplicationFilter;
import uk.gov.gchq.gaffer.hbasestore.retriever.HBaseRetriever;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.operation.handler.OperationHandler;
import uk.gov.gchq.gaffer.user.User;

public class GetAllElementsHandler implements OperationHandler<GetAllElements<Element>, CloseableIterable<Element>> {
    @Override
    public CloseableIterable<Element> doOperation(final GetAllElements<Element> operation, final Context context, final Store store)
            throws OperationException {
        return doOperation(operation, context.getUser(), (HBaseStore) store);
    }

    public CloseableIterable<Element> doOperation(final GetAllElements<Element> operation, final User user, final HBaseStore store) throws OperationException {
        final ElementDeduplicationFilter filter;
        final ElementDeduplicationFilter.ElementDeduplicationFilterProperties rangeFilterProps = new ElementDeduplicationFilter.ElementDeduplicationFilterProperties(operation);
        if (rangeFilterProps.isSkipFilter()) {
            filter = null;
        } else {
            filter = new ElementDeduplicationFilter(rangeFilterProps);
        }

        try {
            return new HBaseRetriever<>(store, operation, user, null, filter);
        } catch (StoreException e) {
            throw new OperationException("Unable to fetch elements", e);
        }
    }
}