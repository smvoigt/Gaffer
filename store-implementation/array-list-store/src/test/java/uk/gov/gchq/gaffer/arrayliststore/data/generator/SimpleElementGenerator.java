/*
 * Copyright 2016 Crown Copyright
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

package uk.gov.gchq.gaffer.arrayliststore.data.generator;

import uk.gov.gchq.gaffer.arrayliststore.data.SimpleEdgeDataObject;
import uk.gov.gchq.gaffer.arrayliststore.data.SimpleEntityDataObject;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.generator.OneToOneElementGenerator;

public class SimpleElementGenerator implements OneToOneElementGenerator<Object> {
    private final SimpleEntityGenerator entityConverter = new SimpleEntityGenerator();
    private final SimpleEdgeGenerator edgeConverter = new SimpleEdgeGenerator();

    @Override
    public Element _apply(final Object obj) {
        if (obj instanceof SimpleEntityDataObject) {
            return entityConverter._apply(((SimpleEntityDataObject) obj));
        }

        if (obj instanceof SimpleEdgeDataObject) {
            return edgeConverter._apply(((SimpleEdgeDataObject) obj));
        }

        throw new IllegalArgumentException("This converter can only handle objects of type SimpleEntityDataObject and SimpleEdgeDataObject");
    }
}