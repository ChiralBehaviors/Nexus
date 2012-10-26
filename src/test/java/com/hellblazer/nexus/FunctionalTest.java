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
import static junit.framework.Assert.*;

/**
 * @author hhildebrand
 * 
 */
public class FunctionalTest {

    private class Listener implements ServiceListener {
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
        int members = 12;
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
            gossip.start();
        }

        GossipScope scope = scopes.get(0);
        ServiceURL url = new ServiceURL(
                                        "service:jmx:http://foo.bar.baz.bozo.com/some/resource/ish/thing");
        scope.register(url, new HashMap<String, String>());

        assertTrue("did not receive all registrations",
                   registered.await(10, TimeUnit.SECONDS));
        assertTrue("did not receive all modifications",
                   modified.await(10, TimeUnit.SECONDS));
        assertTrue("did not receive all unregistrations",
                   unregistered.await(10, TimeUnit.SECONDS));

        for (Listener listener : listeners) {
            assertEquals("Invalid registered count", 1,
                         listener.events.get(EventType.REGISTERED));
            assertEquals("Invalid modified count", 1,
                         listener.events.get(EventType.MODIFIED));
            assertEquals("Invalid unregistered count", 1,
                         listener.events.get(EventType.UNREGISTERED));
        }
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
