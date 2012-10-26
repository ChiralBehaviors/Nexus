package com.hellblazer.nexus;

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

import java.util.Map;
import java.util.UUID;

import com.hellblazer.slp.ServiceReference;
import com.hellblazer.slp.ServiceURL;

/**
 * @author hhildebrand
 *
 */
/**
 * A simple service reference implementation.
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
class ServiceReferenceImpl extends ServiceReference {

    public ServiceReferenceImpl(ServiceURL url, Map<String, String> properties,
                                UUID registration) {
        super(url, properties, registration);
    }

    protected Map<String, String> currentProperties() {
        return properties;
    }

    protected void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}