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

import static junit.framework.Assert.assertEquals;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Test;

import com.hellblazer.gossip.configuration.GossipConfiguration;
import com.hellblazer.slp.ServiceEvent;
import com.hellblazer.slp.ServiceListener;
import com.hellblazer.slp.ServiceScope;
import com.hellblazer.slp.ServiceType;
import com.hellblazer.slp.ServiceURL;

/**
 * @author hhildebrand
 * 
 */
public class GossipScopeTest {
	@Test
	public void testProperties() throws Exception {
		Map<String, String> properties = new HashMap<String, String>();
		char[] value = new char[252];
		Arrays.fill(value, 'v');
		String valueString = new String(value);
		for (int i = 0; i < 3; i++) {
			properties.put(String.valueOf(i), valueString);
		}
		ByteBuffer buffer = ByteBuffer.allocate(800);
		GossipScope.serialize(properties, buffer, 800);
		byte[] state = Arrays.copyOf(buffer.array(), buffer.position());
		Map<String, String> lookedUpProperties = GossipScope.propertiesFrom(
				state, 0);
		assertEquals(properties.size(), lookedUpProperties.size());
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			assertEquals(entry.getValue(),
					lookedUpProperties.get(entry.getKey()));
		}
	}

	@Test
	public void testSerialization() throws Exception {
		InetSocketAddress jmxEndpoint = new InetSocketAddress("localhost", 0);

		UUID registration = UUID.randomUUID();
		// Ensure cryptographically strong random number generater used
		// to choose the object number - see java.rmi.server.ObjID
		System.setProperty("java.rmi.server.randomIDs", "true");

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		JMXServiceURL url = new JMXServiceURL("rmi", jmxEndpoint.getHostName(),
				jmxEndpoint.getPort());
		JMXConnectorServer server = null;
		byte[] serialized;
		ServiceURL jmxServiceURL;
		try {
			server = JMXConnectorServerFactory.newJMXConnectorServer(url,
					new HashMap<String, Object>(), mbs);
			server.start();

			url = server.getAddress();
			StringBuilder builder = new StringBuilder();
			builder.append(ServiceType.SERVICE_PREFIX);
			builder.append("awesome_sauce");
			builder.append(':');
			builder.append("jmx:");
			builder.append(url.getProtocol());
			builder.append("://");
			builder.append(url.getHost());
			builder.append(':');
			builder.append(url.getPort());
			builder.append(url.getURLPath());
			jmxServiceURL = new ServiceURL(builder.toString());

			HashMap<String, String> properties = new HashMap<String, String>();
			GossipScope.normalize(jmxServiceURL, properties, registration);
			serialized = GossipScope.serialize(jmxServiceURL, properties, 1433);
		} finally {
			if (server != null) {
				server.stop();
			}
		}
		ServiceReferenceImpl deserialized = GossipScope.deserialize(
				registration, serialized);
		assertEquals(jmxServiceURL, deserialized.getUrl());
	}

	@Test
	public void testMultipleRegistrations() throws Exception {
		final AtomicInteger count = new AtomicInteger();
		ServiceListener listener = new ServiceListener() {
			@Override
			public void serviceChanged(ServiceEvent event) {
				count.incrementAndGet();
			}
		};

		GossipScope scope = new GossipScope(new Executor() {
			@Override
			public void execute(Runnable command) {
				command.run();
			}
		}, new GossipConfiguration().construct());
		scope.addServiceListener(listener,
				String.format("(%s=service:foo)", ServiceScope.SERVICE_TYPE));
		scope.addServiceListener(listener,
				String.format("(%s=service:bar)", ServiceScope.SERVICE_TYPE));

		scope.register(new ServiceURL("service:foo://foo:5"),
				Collections.<String, String> emptyMap());
		scope.register(new ServiceURL("service:bar://bar:6"),
				Collections.<String, String> emptyMap());

		assertEquals(2, count.get());
	}
}
