package translation;

import models.CFBatchTransaction;
import models.ParallelReaderConfiguration;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.util.Spliterator;
import java.util.concurrent.RecursiveAction;

/**
 * Copyright (C) 2014 Kenny Bastani
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/**
 * The ParallelWriter class recursively generates a thread pool to concurrently distribute writes to HDFS.
 */
public class ParallelReader<T> extends RecursiveAction {
    private static final int THREAD_COUNT = 4;
    private Spliterator<T>[] src;
    private ParallelReaderConfiguration<T> configuration;

    public ParallelReader(Spliterator<T>[] src, ParallelReaderConfiguration<T> configuration) {
        this.src = src;
        this.configuration = configuration;
    }

    @Override
    protected void compute() {
        if (configuration.getLength() <= (configuration.getFullSize() / THREAD_COUNT)) {
            try {
                computeDirectly();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        int split = configuration.getLength() / 2;

        try {
            ParallelReaderConfiguration<T> split1 = configuration.clone();
            ParallelReaderConfiguration<T> split2 = configuration.clone();
            split1.setLength(split);
            split2.setStart(split2.getStart() + split);
            split2.setLength(split2.getLength() - split);
            invokeAll(new ParallelReader<>(src, split1), new ParallelReader<>(src, split2));
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Computes a section of the source array and applies updates to those nodes referenced
     * in each block.
     * @throws IOException
     */
    protected void computeDirectly() throws IOException {
        Transaction tx = ((CFBatchTransaction)configuration).getGraphDatabaseService().beginTx();
        for (int i = configuration.getStart(); i < configuration.getStart() + configuration.getLength(); i++)
            src[i].forEachRemaining(configuration::transactBlock);
        tx.success();
        tx.close();
    }
}
