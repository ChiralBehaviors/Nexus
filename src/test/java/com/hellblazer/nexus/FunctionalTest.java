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
import static junit.framework.Assert.assertTrue;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.hellblazer.gossip.FailureDetectorFactory;
import com.hellblazer.gossip.Gossip;
import com.hellblazer.gossip.SystemView;
import com.hellblazer.gossip.UdpCommunications;
import com.hellblazer.gossip.fd.AdaptiveFailureDetectorFactory;
import com.hellblazer.slp.ServiceEvent;
import com.hellblazer.slp.ServiceEvent.EventType;
import com.hellblazer.slp.ServiceListener;
import com.hellblazer.slp.ServiceScope;
import com.hellblazer.slp.ServiceURL;

/**
 * @author hhildebrand
 * 
 */
public class FunctionalTest {

    private static class Listener implements ServiceListener {
        final Map<EventType, List<ServiceURL>> events = new HashMap<ServiceEvent.EventType, List<ServiceURL>>();

        /**
         * @param registered
         * @param modified
         * @param unregistered
         */
        public Listener(CountDownLatch registered, CountDownLatch modified,
                        CountDownLatch unregistered) {
            this.registered = registered;
            this.modified = modified;
            this.unregistered = unregistered;
            events.put(EventType.REGISTERED, new ArrayList<ServiceURL>());
            events.put(EventType.MODIFIED, new ArrayList<ServiceURL>());
            events.put(EventType.UNREGISTERED, new ArrayList<ServiceURL>());
        }

        final CountDownLatch registered;
        final CountDownLatch modified;
        final CountDownLatch unregistered;

        @Override
        public void serviceChanged(ServiceEvent event) {
            events.get(event.getType()).add(event.getReference().getUrl());
            switch (event.getType()) {
                case REGISTERED: {
                    registered.countDown();
                    break;
                }
                case MODIFIED: {
                    modified.countDown();
                    break;
                }
                case UNREGISTERED: {
                    unregistered.countDown();
                    break;
                }
            }
        }
    }

    @Test
    public void functionalTest() throws Exception {
        int members = 98;
        int maxSeeds = 1;
        final CountDownLatch registered = new CountDownLatch(members);
        final CountDownLatch modified = new CountDownLatch(members);
        final CountDownLatch unregistered = new CountDownLatch(members);
        List<GossipScope> scopes = new ArrayList<GossipScope>();
        List<Listener> listeners = new ArrayList<FunctionalTest.Listener>();
        for (Gossip gossip : createGossips(members, maxSeeds)) {
            GossipScope scope = new GossipScope(gossip);
            Listener listener = new Listener(registered, modified, unregistered);
            scope.addServiceListener(listener,
                                     String.format("(%s=*)",
                                                   ServiceScope.SERVICE_TYPE));
            scopes.add(scope);
            listeners.add(listener);
            scope.start();
        }

        GossipScope scope = scopes.get(0);
        ServiceURL url = new ServiceURL(
                                        "service:jmx:http://foo.bar.baz.bozo.com/some/resource/ish/thing");
        UUID registration = scope.register(url, new HashMap<String, String>());

        System.out.println("Waiting for registrations");
        assertTrue("did not receive all registrations",
                   registered.await(120, TimeUnit.SECONDS));
        for (Listener listener : listeners) {
            assertEquals("Received more than one registration", 1,
                         listener.events.get(EventType.REGISTERED).size());
        }
        System.out.println("All registrations received");

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("update.group", "1");
        properties.put("threads", "2");
        scopes.get(0).setProperties(registration, properties);

        System.out.println("Waiting for modfications");
        assertTrue("did not receive all modifications",
                   modified.await(10, TimeUnit.SECONDS));
        for (Listener listener : listeners) {
            assertEquals("Received more than one modification", 1,
                         listener.events.get(EventType.MODIFIED).size());
        }
        System.out.println("All modifications received");

        scopes.get(0).unregister(registration);
        System.out.println("Waiting for unregistrations");
        assertTrue("did not receive all unregistrations",
                   unregistered.await(10, TimeUnit.SECONDS));
        for (Listener listener : listeners) {
            assertEquals("Received more than one unregistration", 1,
                         listener.events.get(EventType.UNREGISTERED).size());
        }
        System.out.println("All unregistrations received");

    }

    protected List<Gossip> createGossips(int membership, int maxSeeds)
                                                                      throws SocketException {
        Random entropy = new Random(666);
        List<Gossip> members = new ArrayList<Gossip>();
        Collection<InetSocketAddress> seedHosts = new ArrayList<InetSocketAddress>();
        for (int i = 0; i < membership; i++) {
            members.add(createCommunications(seedHosts));
            if (i == 0) { // always add first member
                seedHosts.add(members.get(0).getLocalAddress());
            } else if (seedHosts.size() < maxSeeds) {
                // add the new member with probability of 25%
                if (entropy.nextDouble() < 0.25D) {
                    seedHosts.add(members.get(i).getLocalAddress());
                }
            }
        }
        return members;
    }

    protected Gossip createCommunications(Collection<InetSocketAddress> seedHosts)
                                                                                  throws SocketException {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        e.printStackTrace();
                    }
                });
                return t;
            }
        };
        UdpCommunications communications = new UdpCommunications(
                                                                 new InetSocketAddress(
                                                                                       "127.0.0.1",
                                                                                       0),
                                                                 Executors.newFixedThreadPool(2,
                                                                                              threadFactory));
        SystemView view = new SystemView(new Random(),
                                         communications.getLocalAddress(),
                                         seedHosts, 5000, 500000);
        FailureDetectorFactory fdFactory = new AdaptiveFailureDetectorFactory(
                                                                              0.9,
                                                                              100,
                                                                              0.8,
                                                                              12000,
                                                                              10,
                                                                              3000);
        Gossip gossip = new Gossip(communications, view, fdFactory,
                                   new Random(), 1, TimeUnit.SECONDS, 3);
        return gossip;
    }

}
