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
package uk.gov.gchq.gaffer.spark.examples;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.example.operation.OperationExample;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.data.EdgeSeed;
import uk.gov.gchq.gaffer.operation.data.ElementSeed;
import uk.gov.gchq.gaffer.spark.operation.javardd.GetJavaRDDOfElements;
import uk.gov.gchq.gaffer.user.User;
import java.util.List;

/**
 * An example showing how the {@link GetJavaRDDOfElements} operation is used from Java.
 */
public class GetJavaRDDOfElementsExample extends OperationExample {
    private static final Logger ROOT_LOGGER = Logger.getRootLogger();

    public static void main(final String[] args) throws OperationException {
        new GetJavaRDDOfElementsExample().run();
    }

    public GetJavaRDDOfElementsExample() {
        super(GetJavaRDDOfElements.class);
    }

    @Override
    public void runExamples() {
        // Need to actively turn logging on and off as needed as Spark produces some logs
        // even when the log level is set to off.
        ROOT_LOGGER.setLevel(Level.OFF);
        final SparkConf sparkConf = new SparkConf()
                .setMaster("local")
                .setAppName("GetJavaRDDOfElementsExample")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.kryo.registrator", "uk.gov.gchq.gaffer.spark.serialisation.kryo.Registrator")
                .set("spark.driver.allowMultipleContexts", "true");
        final JavaSparkContext sc = new JavaSparkContext(sparkConf);
        sc.setLogLevel("OFF");
        final Graph graph = getGraph();
        try {
            getJavaRddOfElements(sc, graph);
            getJavaRddOfElementsReturningEdgesOnly(sc, graph);
        } catch (final OperationException e) {
            throw new RuntimeException(e);
        }
        sc.stop();
        ROOT_LOGGER.setLevel(Level.INFO);
    }

    public void getJavaRddOfElements(final JavaSparkContext sc, final Graph graph) throws OperationException {
        ROOT_LOGGER.setLevel(Level.INFO);
        // Avoid using getMethodNameAsSentence as it messes up the formatting of the "RDD" part
        log("#### get Java RDD of elements\n");
        printGraph();
        ROOT_LOGGER.setLevel(Level.OFF);
        final GetJavaRDDOfElements<ElementSeed> operation = new GetJavaRDDOfElements.Builder<>()
                .addSeed(new EdgeSeed(1, 2, true))
                .addSeed(new EdgeSeed(2, 3, true))
                .javaSparkContext(sc)
                .build();
        final JavaRDD<Element> rdd = graph.execute(operation, new User("user01"));
        final List<Element> elements = rdd.collect();
        ROOT_LOGGER.setLevel(Level.INFO);
        printJava("GetJavaRDDOfElements<ElementSeed> operation = new GetJavaRDDOfElements.Builder<>()\n"
                + "                .addSeed(new EdgeSeed(1, 2, true))\n"
                + "                .addSeed(new EdgeSeed(2, 3, true))\n"
                + "                .javaSparkContext(sc)\n"
                + "                .build();\n"
                + "JavaRDD<Element> rdd = graph.execute(operation, new User(\"user01\"));\n"
                + "List<Element> elements = rdd.collect();");
        log("The results are:");
        log("```");
        for (final Element e : elements) {
            log(e.toString());
        }
        log("```");
        ROOT_LOGGER.setLevel(Level.OFF);
    }

    public void getJavaRddOfElementsReturningEdgesOnly(final JavaSparkContext sc, final Graph graph) throws OperationException {
        ROOT_LOGGER.setLevel(Level.INFO);
        log("#### get Java RDD of elements returning edges only\n");
        printGraph();
        ROOT_LOGGER.setLevel(Level.OFF);
        final GetJavaRDDOfElements<ElementSeed> operation = new GetJavaRDDOfElements.Builder<>()
                .addSeed(new EdgeSeed(1, 2, true))
                .addSeed(new EdgeSeed(2, 3, true))
                .includeEntities(false)
                .javaSparkContext(sc)
                .build();
        final JavaRDD<Element> rdd = graph.execute(operation, new User("user01"));
        final List<Element> elements = rdd.collect();
        ROOT_LOGGER.setLevel(Level.INFO);
        printJava("GetJavaRDDOfElements<ElementSeed> operation = new GetJavaRDDOfElements.Builder<>()\n"
                + "                .addSeed(new EdgeSeed(1, 2, true))\n"
                + "                .addSeed(new EdgeSeed(2, 3, true))\n"
                + "                .includeEntities(false)\n"
                + "                .javaSparkContext(sc)\n"
                + "                .build();\n"
                + "JavaRDD<Element> rdd = graph.execute(operation, new User(\"user01\"));\n"
                + "List<Element> elements = rdd.collect();");
        log("The results are:");
        log("```");
        for (final Element e : elements) {
            log(e.toString());
        }
        log("```");
        ROOT_LOGGER.setLevel(Level.OFF);
    }

}
