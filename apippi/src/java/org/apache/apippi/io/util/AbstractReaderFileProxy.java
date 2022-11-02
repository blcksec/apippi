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

package org.apache.apippi.io.util;

public abstract class AbstractReaderFileProxy implements ReaderFileProxy
{
    protected final ChannelProxy channel;
    protected final long fileLength;

    protected AbstractReaderFileProxy(ChannelProxy channel, long fileLength)
    {
        this.channel = channel;
        this.fileLength = fileLength >= 0 ? fileLength : channel.size();
    }

    @Override
    public ChannelProxy channel()
    {
        return channel;
    }

    @Override
    public long fileLength()
    {
        return fileLength;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "(filePath='" + channel + "')";
    }

    @Override
    public void close()
    {
        // nothing in base class
    }

    @Override
    public double getCrcCheckChance()
    {
        return 0; // Only valid for compressed files.
    }
}
