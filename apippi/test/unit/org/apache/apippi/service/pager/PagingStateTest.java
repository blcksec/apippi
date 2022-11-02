/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.apippi.service.pager;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.apippi.Util;
import org.apache.apippi.config.DatabaseDescriptor;
import org.apache.apippi.transport.ProtocolVersion;
import org.apache.apippi.utils.ByteBufferUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PagingStateTest
{
    @BeforeClass
    public static void setupDD()
    {
        DatabaseDescriptor.daemonInitialization();
    }

    @Test
    public void testSerializationBackwardCompatibility()
    {
        /*
         * Tests that the serialized paging state for the native protocol V3 is backward compatible
         * with what old nodes generate. For that, it compares the serialized format to the hard-coded
         * value of the same state generated on a 2.1. For the curious, said hardcoded value has been
         * generated by the following code:
         *     ByteBuffer pk = ByteBufferUtil.bytes("someKey");
         *     CellName cn = CellNames.compositeSparse(new ByteBuffer[]{ ByteBufferUtil.bytes("c1"), ByteBufferUtil.bytes(42) },
         *                                             new ColumnIdentifier("myCol", false),
         *                                             false);
         *     PagingState state = new PagingState(pk, cn.toByteBuffer(), 10);
         *     System.out.println("PagingState = " + ByteBufferUtil.bytesToHex(state.serialize()));
         */
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V3);

        String serializedState = ByteBufferUtil.bytesToHex(state.serialize(ProtocolVersion.V3));
        // Note that we don't assert exact equality because we know 3.0 nodes include the "remainingInPartition" number
        // that is not present on 2.1/2.2 nodes. We know this is ok however because we know that 2.1/2.2 nodes will ignore
        // anything remaining once they have properly deserialized a paging state.
        assertTrue(serializedState.startsWith("0007736f6d654b65790014000263310000040000002a0000056d79636f6c000000000a"));
    }

    @Test
    public void testSerializeV3DeserializeV3()
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V3);
        ByteBuffer serialized = state.serialize(ProtocolVersion.V3);
        assertEquals(serialized.remaining(), state.serializedSize(ProtocolVersion.V3));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V3));
    }

    @Test
    public void testSerializeV4DeserializeV4()
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V4);
        ByteBuffer serialized = state.serialize(ProtocolVersion.V4);
        assertEquals(serialized.remaining(), state.serializedSize(ProtocolVersion.V4));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V4));
    }

    @Test
    public void testSerializeV5DeserializeV5()
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V5);
        ByteBuffer serialized = state.serialize(ProtocolVersion.V5);
        assertEquals(serialized.remaining(), state.serializedSize(ProtocolVersion.V5));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V5));
    }

    @Test
    public void testSerializeV3DeserializeV4()
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V3);
        ByteBuffer serialized = state.serialize(ProtocolVersion.V3);
        assertEquals(serialized.remaining(), state.serializedSize(ProtocolVersion.V3));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V4));
    }

    @Test
    public void testSerializeV4DeserializeV3()
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V4);
        ByteBuffer serialized = state.serialize(ProtocolVersion.V4);
        assertEquals(serialized.remaining(), state.serializedSize(ProtocolVersion.V4));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V3));
    }

    @Test
    public void testSerializeV3WithoutRemainingInPartitionDeserializeV3() throws IOException
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V3, Integer.MAX_VALUE);
        ByteBuffer serialized = state.legacySerialize(false);
        assertEquals(serialized.remaining(), state.legacySerializedSize(false));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V3));
    }

    @Test
    public void testSerializeV3WithoutRemainingInPartitionDeserializeV4() throws IOException
    {
        PagingState state = Util.makeSomePagingState(ProtocolVersion.V3, Integer.MAX_VALUE);
        ByteBuffer serialized = state.legacySerialize(false);
        assertEquals(serialized.remaining(), state.legacySerializedSize(false));
        assertEquals(state, PagingState.deserialize(serialized, ProtocolVersion.V4));
    }
}
