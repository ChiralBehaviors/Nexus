/** 
 * (C) Copyright 2010 Hal Hildebrand, All Rights Reserved
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

package com.hellblazer.nexus;

import java.util.UUID;

import com.hellblazer.gossip.GossipListener;

/**
 * @author hhildebrand
 * 
 */
public class GossipDispatcher implements GossipListener {

    private GossipScope scope;

    /* (non-Javadoc)
     * @see com.hellblazer.gossip.GossipListener#deregister(java.util.UUID)
     */
    @Override
    public void deregister(UUID id) {
        if (scope != null) {
            scope.deregister(id);
        }
    }

    /* (non-Javadoc)
     * @see com.hellblazer.gossip.GossipListener#register(java.util.UUID, byte[])
     */
    @Override
    public void register(UUID id, byte[] state) {
        if (scope != null) {
            scope.register(id, state);
        }
    }

    /* (non-Javadoc)
     * @see com.hellblazer.gossip.GossipListener#update(java.util.UUID, byte[])
     */
    @Override
    public void update(UUID id, byte[] state) {
        if (scope != null) {
            scope.update(id, state);
        }
    }

    /**
     * @param scope
     *            the scope to set
     */
    public void setScope(GossipScope scope) {
        this.scope = scope;
    }

}
