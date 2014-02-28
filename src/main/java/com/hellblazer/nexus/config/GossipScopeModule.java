/*
 * (C) Copyright 2014 Chiral Behaviors, All Rights Reserved
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

package com.hellblazer.nexus.config;

import com.hellblazer.gossip.configuration.GossipModule;
import com.hellblazer.slp.config.ServiceScopeConfiguration;

/**
 * @author hhildebrand
 *
 */
public class GossipScopeModule extends GossipModule {

    private static final long serialVersionUID = 1L;

    public GossipScopeModule() {
        super("Gossip Scope Module");
    }

    public GossipScopeModule(String name) {
        super(name);
    }

    @Override
    public void setupModule(SetupContext context) {
        setMixInAnnotation(ServiceScopeConfiguration.class,
                                    GossipScopeConfiguration.class);
        super.setupModule(context);
    }
}
