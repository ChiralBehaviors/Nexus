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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ring<T> {
    private final GossipCommunications<T>            comms;
    private final AtomicReference<InetSocketAddress> neighbor = new AtomicReference<InetSocketAddress>();
    private final UUID                               id;
    private static final Logger                      log      = LoggerFactory.getLogger(Ring.class.getCanonicalName());

    public Ring(UUID identity, GossipCommunications<T> comms) {
        id = identity;
        this.comms = comms;
    }

    /**
     * Send the heartbeat around the ring in both directions.
     * 
     * @param state
     */
    public void send(ReplicatedState<T> state) {
        InetSocketAddress l = neighbor.get();
        if (l == null) {
            if (log.isTraceEnabled()) {
                log.trace("Ring has not been formed, not forwarding state");
            }
            return;
        }
        comms.send(state, l);
    }

    /**
     * Update the neighboring members of the id on the ring represented by the
     * members.
     * 
     * @param members
     * @param endpoints
     */
    public void update(SortedSet<UUID> members,
                       Collection<Endpoint<T>> endpoints) {
        UUID n = leftNeighborOf(members, id);
        if (n == null) {
            if (log.isTraceEnabled()) {
                log.trace(String.format("id {%s} does not have a left neighbor in: %s",
                                        id, members));
            }
            return;
        }
        InetSocketAddress l = null;
        for (Endpoint<T> endpoint : endpoints) {
            UUID identity = endpoint.getState().getId();
            if (identity != null) {
                UUID eid = identity;
                if (eid == n) {
                    l = endpoint.getState().getAddress();
                    break;
                }
            }
        }
        if (l == null) {
            if (log.isTraceEnabled()) {
                log.trace("Ring has not been formed");
            }
            neighbor.set(null);
        } else {
            neighbor.set(l);
        }
    }

    /**
     * @param id
     * @return
     */
    private UUID leftNeighborOf(SortedSet<UUID> members, UUID id) {
        SortedSet<UUID> head = members.headSet(id);
        return head.isEmpty() ? null : head.last();
    }
}
