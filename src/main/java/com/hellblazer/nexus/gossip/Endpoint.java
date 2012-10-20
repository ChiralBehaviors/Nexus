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
package com.hellblazer.nexus.gossip;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Endpoint keeps track of the heartbeat state and the failure detector for
 * remote clients
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */

public class Endpoint<T> {
    protected static Logger logger = LoggerFactory.getLogger(Endpoint.class);

    public static InetSocketAddress readInetAddress(ByteBuffer msg)
                                                                   throws UnknownHostException {
        int length = msg.get();
        if (length == 0) {
            return null;
        }

        byte[] address = new byte[length];
        msg.get(address);
        int port = msg.getInt();

        InetAddress inetAddress = InetAddress.getByAddress(address);
        return new InetSocketAddress(inetAddress, port);
    }

    public static void writeInetAddress(InetSocketAddress ipaddress,
                                        ByteBuffer bytes) {
        if (ipaddress == null) {
            bytes.put((byte) 0);
            return;
        }
        byte[] address = ipaddress.getAddress().getAddress();
        bytes.put((byte) address.length);
        bytes.put(address);
        bytes.putInt(ipaddress.getPort());
    }

    private final FailureDetector       fd;
    private volatile GossipMessages<T>  handler;
    private volatile ReplicatedState<T> state;
    private volatile boolean            isAlive = true;

    public Endpoint() {
        fd = null;
    }

    public Endpoint(ReplicatedState<T> replicatedState,
                    FailureDetector failureDetector) {
        state = replicatedState;
        fd = failureDetector;
    }

    public long getEpoch() {
        return state.getEpoch();
    }

    public GossipMessages<T> getHandler() {
        return handler;
    }

    public ReplicatedState<T> getState() {
        return state;
    }

    public long getTime() {
        return state.getTime();
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void markAlive() {
        isAlive = true;
    }

    public void markDead() {
        isAlive = false;
    }

    public void record(ReplicatedState<T> newState) {
        if (state != newState) {
            state = newState;
            fd.record(state.getTime(), System.currentTimeMillis());
        }
    }

    public void setCommunications(GossipMessages<T> communications) {
        handler = communications;
    }

    /**
     * Answer true if the suspicion level of the failure detector is greater
     * than the conviction threshold
     * 
     * @param now
     *            - the time at which to base the measurement
     * @return true if the suspicion level of the failure detector is greater
     *         than the conviction threshold
     */
    public boolean shouldConvict(long now) {
        return fd.shouldConvict(now);
    }

    @Override
    public String toString() {
        return "Endpoint " + state.getId();
    }

    public void updateState(ReplicatedState<T> newState) {
        state = newState;
        if (logger.isTraceEnabled()) {
            logger.trace(String.format("new replicated state time: %s",
                                       state.getTime()));
        }
    }
}
