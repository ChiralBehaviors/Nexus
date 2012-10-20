/** (C) Copyright 2010 Hal Hildebrand, All Rights Reserved
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
package com.hellblazer.jackal.gossip;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import junit.framework.TestCase;

import org.smartfrog.services.anubis.partition.util.Identity;
import org.smartfrog.services.anubis.partition.util.NodeIdSet;
import org.smartfrog.services.anubis.partition.views.View;

import com.hellblazer.nexus.gossip.GossipMessages;
import com.hellblazer.nexus.gossip.HeartbeatState;

/**
 * Basic testing of the heartbeat state
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
public class HeartbeatStateTest extends TestCase {
    public void testBasic() throws Exception {
        NodeIdSet msgLinks = new NodeIdSet();
        Identity candidate = new Identity(0x1638, Identity.MAX_ID, 667);
        boolean preferred = true;
        Identity sender = new Identity(0x1638, 23, 22);
        InetSocketAddress heartbeatAddress = new InetSocketAddress("localhost",
                                                                   80);
        InetSocketAddress senderAddress = new InetSocketAddress("localhost", 80);
        boolean stable = true;
        InetSocketAddress testInterface = new InetSocketAddress("localhost",
                                                                443);
        NodeIdSet v = new NodeIdSet();
        long viewNumber = 128L;
        long viewTimestamp = 990876L;
        long time = 564567L;

        msgLinks.add(candidate.id);
        msgLinks.add(sender.id);
        v.add(candidate.id);
        HeartbeatState state = new HeartbeatState(candidate, true,
                                                  heartbeatAddress, msgLinks,
                                                  preferred, sender,
                                                  senderAddress, stable,
                                                  testInterface, v, viewNumber,
                                                  viewTimestamp);
        state.setTime(time);
        assertSame(msgLinks, state.getMsgLinks());
        assertSame(sender, state.getSender());
        assertSame(senderAddress, state.getSenderAddress());
        assertSame(testInterface, state.getControllerInterface());
        assertSame(heartbeatAddress, state.getHeartbeatAddress());
        assertEquals(preferred, state.isPreferred());
        assertTrue(state.getMsgLinks().contains(candidate.id));
        assertTrue(state.getMsgLinks().contains(sender.id));
        assertEquals(time, state.getTime());

        View view = state.getView();

        assertNotNull(view);
        assertTrue(view.contains(candidate));
        assertFalse(view.contains(sender));
        assertEquals(viewNumber, state.getViewNumber());
        assertEquals(viewTimestamp, view.getTimeStamp());
        assertEquals(stable, view.isStable());

        byte[] bytes = new byte[GossipMessages.HEARTBEAT_STATE_BYTE_SIZE];
        ByteBuffer msg = ByteBuffer.wrap(bytes);
        state.writeTo(msg);
        msg.flip();

        HeartbeatState dState = new HeartbeatState(msg);
        assertEquals(msgLinks, dState.getMsgLinks());
        assertEquals(sender, dState.getSender());
        assertEquals(senderAddress, dState.getSenderAddress());
        assertEquals(testInterface, dState.getControllerInterface());
        assertEquals(preferred, dState.isPreferred());
        assertEquals(heartbeatAddress, state.getHeartbeatAddress());
        assertTrue(dState.getMsgLinks().contains(candidate.id));
        assertTrue(dState.getMsgLinks().contains(sender.id));
        assertEquals(time, dState.getTime());

        view = dState.getView();

        assertNotNull(view);
        assertTrue(view.contains(candidate));
        assertFalse(view.contains(sender));
        assertEquals(viewNumber, dState.getViewNumber());
        assertEquals(viewTimestamp, view.getTimeStamp());
        assertEquals(stable, view.isStable());
    }
}
