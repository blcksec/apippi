/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.apippi.db.compaction;

import java.util.Set;

import org.apache.apippi.db.ColumnFamilyStore;
import org.apache.apippi.db.lifecycle.LifecycleTransaction;
import org.apache.apippi.io.sstable.format.SSTableReader;

public class TimeWindowCompactionTask extends CompactionTask
{
    private final boolean ignoreOverlaps;

    public TimeWindowCompactionTask(ColumnFamilyStore cfs, LifecycleTransaction txn, int gcBefore, boolean ignoreOverlaps)
    {
        super(cfs, txn, gcBefore);
        this.ignoreOverlaps = ignoreOverlaps;
    }

    @Override
    public CompactionController getCompactionController(Set<SSTableReader> toCompact)
    {
        return new TimeWindowCompactionController(cfs, toCompact, gcBefore, ignoreOverlaps);
    }
}
