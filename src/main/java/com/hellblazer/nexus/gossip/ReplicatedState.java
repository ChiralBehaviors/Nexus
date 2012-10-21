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

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * @author hhildebrand
 * 
 */
public class ReplicatedState<T extends Serializable> {
    private final long              epoch = -1;
    private final UUID              id;
    private final T                 state;
    private volatile long           time  = -1L;
    private final InetSocketAddress address;

    public ReplicatedState(InetSocketAddress address) {
        this(address, null, null);
    }

    /**
     * @param id
     * @param state
     */
    public ReplicatedState(InetSocketAddress address, UUID id, T state) {
        this.address = address;
        this.id = id;
        this.state = state;
    }

    /**
     * @param msg
     */
    public ReplicatedState(ByteBuffer msg) {
        this(null, null, null);
    }

    /**
     * @return the address
     */
    public InetSocketAddress getAddress() {
        return address;
    }

    /**
     * @return the epoch
     */
    public long getEpoch() {
        return epoch;
    }

    /**
     * @return the id
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return the state
     */
    public T getState() {
        return state;
    }

    /**
     * @return the time
     */
    public long getTime() {
        return time;
    }

    /**
     * @param time
     *            the time to set
     */
    public void setTime(long time) {
        this.time = time;
    }

    /**
     * @param buffer
     */
    public void writeTo(ByteBuffer buffer) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("ReplicatedState [epoch=%s, id=%s, time=%s, address=%s]",
                             epoch, id, time, address);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (epoch ^ (epoch >>> 32));
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (time ^ (time >>> 32));
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ReplicatedState<?> other = (ReplicatedState<?>) obj;
        if (epoch != other.epoch)
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (time != other.time)
            return false;
        return true;
    }
}
