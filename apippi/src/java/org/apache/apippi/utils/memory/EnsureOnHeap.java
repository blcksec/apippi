/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.apippi.utils.memory;

import java.util.Iterator;

import org.apache.apippi.db.BufferDecoratedKey;
import org.apache.apippi.db.Clustering;
import org.apache.apippi.db.DecoratedKey;
import org.apache.apippi.db.DeletionInfo;
import org.apache.apippi.db.rows.*;
import org.apache.apippi.db.transform.Transformation;
import org.apache.apippi.utils.SearchIterator;

public abstract class EnsureOnHeap extends Transformation
{
    public static final EnsureOnHeap NOOP = new NoOp();

    public abstract DecoratedKey applyToPartitionKey(DecoratedKey key);
    public abstract UnfilteredRowIterator applyToPartition(UnfilteredRowIterator partition);
    public abstract SearchIterator<Clustering<?>, Row> applyToPartition(SearchIterator<Clustering<?>, Row> partition);
    public abstract Iterator<Row> applyToPartition(Iterator<Row> partition);
    public abstract DeletionInfo applyToDeletionInfo(DeletionInfo deletionInfo);
    public abstract Row applyToRow(Row row);
    public abstract Row applyToStatic(Row row);
    public abstract RangeTombstoneMarker applyToMarker(RangeTombstoneMarker marker);

    static class CloneToHeap extends EnsureOnHeap
    {
        protected BaseRowIterator<?> applyToPartition(BaseRowIterator partition)
        {
            return partition instanceof UnfilteredRowIterator
                   ? Transformation.apply((UnfilteredRowIterator) partition, this)
                   : Transformation.apply((RowIterator) partition, this);
        }

        public DecoratedKey applyToPartitionKey(DecoratedKey key)
        {
            return new BufferDecoratedKey(key.getToken(), HeapCloner.instance.clone(key.getKey()));
        }

        public Row applyToRow(Row row)
        {
            if (row == null)
                return null;
            return row.clone(HeapCloner.instance);
        }

        public Row applyToStatic(Row row)
        {
            if (row == Rows.EMPTY_STATIC_ROW)
                return row;
            return applyToRow(row);
        }

        public RangeTombstoneMarker applyToMarker(RangeTombstoneMarker marker)
        {
            return marker.clone(HeapCloner.instance);
        }

        public UnfilteredRowIterator applyToPartition(UnfilteredRowIterator partition)
        {
            return Transformation.apply(partition, this);
        }

        public SearchIterator<Clustering<?>, Row> applyToPartition(SearchIterator<Clustering<?>, Row> partition)
        {
            return new SearchIterator<Clustering<?>, Row>()
            {
                public Row next(Clustering<?> key)
                {
                    return applyToRow(partition.next(key));
                }
            };
        }

        public Iterator<Row> applyToPartition(Iterator<Row> partition)
        {
            return new Iterator<Row>()
            {
                public boolean hasNext()
                {
                    return partition.hasNext();
                }
                public Row next()
                {
                    return applyToRow(partition.next());
                }
                public void remove()
                {
                    partition.remove();
                }
            };
        }

        public DeletionInfo applyToDeletionInfo(DeletionInfo deletionInfo)
        {
            return deletionInfo.clone(HeapCloner.instance);
        }
    }

    static class NoOp extends EnsureOnHeap
    {
        protected BaseRowIterator<?> applyToPartition(BaseRowIterator partition)
        {
            return partition;
        }

        public DecoratedKey applyToPartitionKey(DecoratedKey key)
        {
            return key;
        }

        public Row applyToRow(Row row)
        {
            return row;
        }

        public Row applyToStatic(Row row)
        {
            return row;
        }

        public RangeTombstoneMarker applyToMarker(RangeTombstoneMarker marker)
        {
            return marker;
        }

        public UnfilteredRowIterator applyToPartition(UnfilteredRowIterator partition)
        {
            return partition;
        }

        public SearchIterator<Clustering<?>, Row> applyToPartition(SearchIterator<Clustering<?>, Row> partition)
        {
            return partition;
        }

        public Iterator<Row> applyToPartition(Iterator<Row> partition)
        {
            return partition;
        }

        public DeletionInfo applyToDeletionInfo(DeletionInfo deletionInfo)
        {
            return deletionInfo;
        }
    }
}
