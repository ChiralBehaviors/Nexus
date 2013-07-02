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

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hellblazer.gossip.Gossip;
import com.hellblazer.gossip.GossipListener;
import com.hellblazer.slp.Filter;
import com.hellblazer.slp.InvalidSyntaxException;
import com.hellblazer.slp.ServiceEvent;
import com.hellblazer.slp.ServiceEvent.EventType;
import com.hellblazer.slp.ServiceListener;
import com.hellblazer.slp.ServiceReference;
import com.hellblazer.slp.ServiceScope;
import com.hellblazer.slp.ServiceType;
import com.hellblazer.slp.ServiceURL;

/**
 * A service discovery scope based on the Chinese Whispers gossip based state
 * replication service.
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
public class GossipScope implements ServiceScope {
    private class GossipDispatcher implements GossipListener {

        /*
         * (non-Javadoc)
         * 
         * @see com.hellblazer.gossip.GossipListener#deregister(java.util.UUID)
         */
        @Override
        public void deregister(UUID id) {
            GossipScope.this.deregister(id);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.hellblazer.gossip.GossipListener#register(java.util.UUID,
         * byte[])
         */
        @Override
        public void register(UUID id, byte[] state) {
            GossipScope.this.register(id, state);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.hellblazer.gossip.GossipListener#update(java.util.UUID,
         * byte[])
         */
        @Override
        public void update(UUID id, byte[] state) {
            GossipScope.this.update(id, state);
        }

    }

    private static class ListenerRegistration implements
            Comparable<ListenerRegistration> {
        private final ServiceListener listener;
        private final Filter          query;

        /**
         * @param listener
         * @param fileter
         */
        public ListenerRegistration(ServiceListener listener, Filter filter) {
            this.listener = listener;
            query = filter;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(ListenerRegistration reg) {
            if (listener.equals(reg.listener)) {
                return query.compareTo(reg.query);
            } else {
                return new Integer(listener.hashCode()).compareTo(new Integer(
                                                                              reg.hashCode()));
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ListenerRegistration other = (ListenerRegistration) obj;
            if (listener == null) {
                if (other.listener != null) {
                    return false;
                }
            } else if (!listener.equals(other.listener)) {
                return false;
            }
            if (query == null) {
                if (other.query != null) {
                    return false;
                }
            } else if (!query.equals(other.query)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                     + (listener == null ? 0 : listener.hashCode());
            result = prime * result + (query == null ? 0 : query.hashCode());
            return result;
        }
    }

    private final static Logger log                     = LoggerFactory.getLogger(GossipScope.class);

    private static final int    MAXIMUM_TXT_STRING_SIZE = 255;

    /**
     * @param url
     * @param properties
     * @param registration
     */
    public static Map<String, String> denormalize(Map<String, String> properties) {
        Map<String, String> denorm = new HashMap<String, String>(properties);
        denorm.remove(SERVICE_TYPE);
        denorm.remove(SERVICE_REGISTRATION);
        denorm.remove(SERVICE_URL_PATH);
        return denorm;
    }

    /**
     * @param id
     * @param state
     * @return
     */
    public static ServiceReferenceImpl deserialize(UUID id, byte[] state) {
        byte weight = state[0];
        byte priority = state[1];
        int len = state[2] << 8 | state[3] & 0xff;
        String url = "service:" + new String(state, 4, len);
        Map<String, String> properties = propertiesFrom(state, len + 4);
        ServiceURL serviceUrl;
        try {
            serviceUrl = new ServiceURL(url, weight, priority);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(
                                            String.format("Invalid service url: %s",
                                                          url), e);
        }
        normalize(serviceUrl, properties, id);
        return new ServiceReferenceImpl(serviceUrl, properties, id);
    }

    /**
     * Answer the list of strings from the offset in the state
     * 
     * @param state
     * @param offset
     * @return
     */
    public static List<String> getStrings(byte[] state, int offset) {
        List<String> strings = new ArrayList<String>();
        for (int i = offset; i < state.length;) {
            int len = state[i] & 0xFF;
            strings.add(new String(state, i + 1, len));
            i += len + 1;
        }
        return strings;
    }

    /**
     * @param url
     * @param properties
     * @param registration
     */
    public static void normalize(ServiceURL url,
                                 Map<String, String> properties,
                                 UUID registration) {
        properties.put(SERVICE_TYPE, url.getServiceType().toString());
        properties.put(SERVICE_REGISTRATION, registration.toString());
        properties.put(SERVICE_URL_PATH, url.getUrlPath());
    }

    /**
     * reconstitute a properties map from the serialized state
     * 
     * @param state
     * @param offset
     * @return
     */
    public static Map<String, String> propertiesFrom(byte[] state, int offset) {
        Map<String, String> properties = new HashMap<String, String>();
        for (String entry : getStrings(state, offset)) {
            if (entry.length() != 0) {
                int i = entry.indexOf('=');
                if (i <= 0) {
                    log.warn(String.format("Found invalid property entry %s ",
                                           entry));
                }
                properties.put(entry.substring(0, i), entry.substring(i + 1));
            }
        }
        return properties;
    }

    public static void serialize(Map<String, String> properties,
                                 ByteBuffer buffer, int maxSize) {
        assert properties != null : "properties must not be null";

        int totalSize = 0;
        List<String> strings = new ArrayList<String>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String encodedEntry = String.format("%s=%s", entry.getKey(),
                                                entry.getValue());
            if (encodedEntry.length() > MAXIMUM_TXT_STRING_SIZE) {
                throw new IllegalArgumentException(
                                                   String.format("Property entry %s exceeded maximum size %s, total size %s",
                                                                 encodedEntry,
                                                                 MAXIMUM_TXT_STRING_SIZE,
                                                                 encodedEntry.length()));
            } else {
                totalSize += encodedEntry.length();
                strings.add(encodedEntry);
            }
        }
        if (totalSize > maxSize) {
            throw new IllegalArgumentException(
                                               String.format("serialized property size exceeded maximum size %s, total size %s : %s",
                                                             maxSize,
                                                             totalSize,
                                                             properties));
        }
        for (String entry : strings) {
            buffer.put((byte) entry.length());
            buffer.put(entry.getBytes());
        }
    }

    /**
     * @param url
     * @param properties
     * @param maxStateSize
     * @return
     * @throws IOException
     */
    public static byte[] serialize(ServiceURL url,
                                   Map<String, String> properties,
                                   int maxStateSize) {
        properties = denormalize(properties);
        String serviceUrl = url.toString().substring(ServiceType.SERVICE_PREFIX.length());
        ByteBuffer buffer = ByteBuffer.wrap(new byte[maxStateSize]);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) url.getWeight());
        buffer.put((byte) url.getPriority());
        buffer.putShort((short) serviceUrl.length());
        buffer.put(serviceUrl.getBytes());
        serialize(properties, buffer, maxStateSize - buffer.position());
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private final Executor                        executor;
    private final Gossip                          gossip;
    private final Set<ListenerRegistration>       listeners = new ConcurrentSkipListSet<ListenerRegistration>();
    private final Map<UUID, ServiceReferenceImpl> services  = new ConcurrentHashMap<UUID, ServiceReferenceImpl>();

    public GossipScope(Executor execService, Gossip gossip) {
        executor = execService;
        this.gossip = gossip;
        this.gossip.setListener(new GossipDispatcher());
    }

    public GossipScope(Gossip gossip) {
        this(gossip, 2);
    }

    public GossipScope(Gossip gossip, int notificationThreads) {
        this(Executors.newFixedThreadPool(notificationThreads,
                                          new ThreadFactory() {
                                              int i = 0;

                                              @Override
                                              public Thread newThread(Runnable arg0) {
                                                  Thread daemon = new Thread(
                                                                             arg0,
                                                                             String.format("GossipScope dispatcher[%s]",
                                                                                           i++));
                                                  daemon.setDaemon(true);
                                                  daemon.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

                                                      @Override
                                                      public void uncaughtException(Thread t,
                                                                                    Throwable e) {
                                                          log.warn(String.format("Uncaught exception on [%s]",
                                                                                 t),
                                                                   e);
                                                      }
                                                  });
                                                  return daemon;
                                              }
                                          }), gossip);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#addServiceListener(com.hellblazer.slp
     * .ServiceListener, java.lang.String)
     */
    @Override
    public void addServiceListener(final ServiceListener listener, String query)
                                                                                throws InvalidSyntaxException {
        Filter filter = new Filter(query);
        if (listeners.add(new ListenerRegistration(listener, filter))) {
            if (log.isInfoEnabled()) {
                log.info(String.format("Adding listener on %s", query));
            }
        } else {
            log.warn(String.format("Did not add listener on %s", query));
            return;
        }
        for (ServiceReference reference : services.values()) {
            final ServiceReference ref = reference;
            if (filter.match(ref)) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.serviceChanged(new ServiceEvent(
                                                                     EventType.REGISTERED,
                                                                     ref));
                        } catch (Throwable e) {
                            log.error("Error when notifying listener on reference "
                                              + EventType.REGISTERED, e);
                        }
                    }
                });
            }
        }

    }

    /* (non-Javadoc)
     * @see com.hellblazer.slp.ServiceScope#getReference(java.util.UUID)
     */
    @Override
    public ServiceReference getReference(UUID serviceRegistration) {
        return services.get(serviceRegistration);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#getServiceReference(java.lang.String)
     */
    @Override
    public ServiceReference getServiceReference(String serviceType)
                                                                   throws InvalidSyntaxException {
        if (serviceType == null) {
            serviceType = "*";
        }
        Filter filter = new Filter("(" + SERVICE_TYPE + "=" + serviceType + ")");
        for (ServiceReference ref : services.values()) {
            if (filter.match(ref)) {
                return ref;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#getServiceReferences(java.lang.String,
     * java.lang.String)
     */
    @Override
    public List<ServiceReference> getServiceReferences(String serviceType,
                                                       String query)
                                                                    throws InvalidSyntaxException {
        if (serviceType == null) {
            serviceType = "*";
        }
        Filter filter;
        if (query == null) {
            filter = new Filter(String.format("(%s=%s)", SERVICE_TYPE,
                                              serviceType));
        } else {
            filter = new Filter(String.format("(&(%s=%s) %s)", SERVICE_TYPE,
                                              serviceType, query));
        }
        ArrayList<ServiceReference> references = new ArrayList<ServiceReference>();
        for (Map.Entry<UUID, ServiceReferenceImpl> entry : services.entrySet()) {
            if (filter.match(entry.getValue())) {
                references.add(entry.getValue());
            }
        }
        return references;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#register(com.hellblazer.slp.ServiceURL,
     * java.util.Map)
     */
    @Override
    public UUID register(ServiceURL url, Map<String, String> properties) {
        if (url == null) {
            throw new IllegalArgumentException("Service URL cannot be null");
        }
        UUID registration = gossip.register(serialize(url, properties,
                                                      gossip.getMaxStateSize()));
        if (properties == null) {
            properties = new HashMap<String, String>();
        }
        properties = new HashMap<String, String>(properties);
        normalize(url, properties, registration);
        ServiceReferenceImpl ref = new ServiceReferenceImpl(url, properties,
                                                            registration);
        services.put(registration, ref);
        serviceChanged(ref, EventType.REGISTERED);
        return registration;

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#removeServiceListener(com.hellblazer.
     * slp.ServiceListener)
     */
    @Override
    public void removeServiceListener(ServiceListener listener) {
        List<ListenerRegistration> registrations = new ArrayList<ListenerRegistration>();
        for (ListenerRegistration reg : listeners) {
            if (reg.listener == listener) {
                registrations.add(reg);
            }
        }
        listeners.removeAll(registrations);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.hellblazer.slp.ServiceScope#removeServiceListener(com.hellblazer.
     * slp.ServiceListener, java.lang.String)
     */
    @Override
    public void removeServiceListener(ServiceListener listener, String query)
                                                                             throws InvalidSyntaxException {
        listeners.remove(new ListenerRegistration(listener, new Filter(query)));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.hellblazer.slp.ServiceScope#setProperties(java.util.UUID,
     * java.util.Map)
     */
    @Override
    public void setProperties(UUID serviceRegistration,
                              Map<String, String> properties) {
        ServiceReferenceImpl ref = services.get(serviceRegistration);
        if (ref == null) {
            if (log.isTraceEnabled()) {
                log.trace(String.format("No service registered for %s",
                                        serviceRegistration));
            }
            return;
        }
        properties = new HashMap<String, String>(properties);
        properties.put(SERVICE_TYPE, ref.currentProperties().get(SERVICE_TYPE));
        ref.setProperties(properties);
        gossip.update(serviceRegistration,
                      serialize(ref.getUrl(), ref.getProperties(),
                                gossip.getMaxStateSize()));
        serviceChanged(ref, EventType.MODIFIED);
    }

    @Override
    public GossipScope start() {
        gossip.start();
        return this;
    }

    @Override
    public ServiceScope stop() {
        gossip.terminate();
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.hellblazer.slp.ServiceScope#unregister(java.util.UUID)
     */
    @Override
    public void unregister(UUID serviceRegistration) {
        ServiceReference ref = services.remove(serviceRegistration);
        if (ref != null) {
            gossip.deregister(serviceRegistration);
            serviceChanged(ref, EventType.UNREGISTERED);
        } else {
            if (log.isTraceEnabled()) {
                log.trace(String.format("No service registered for %s",
                                        serviceRegistration));
            }
        }
    }

    /**
     * @param id
     */
    protected void deregister(UUID id) {
        ServiceReference reference = services.remove(id);
        if (reference != null) {
            serviceChanged(reference, EventType.UNREGISTERED);
        }
    }

    /**
     * @param id
     * @param state
     */
    protected void register(UUID id, byte[] state) {
        ServiceReferenceImpl reference = deserialize(id, state);
        services.put(id, reference);
        serviceChanged(reference, EventType.REGISTERED);
    }

    protected void serviceChanged(final ServiceReference reference,
                                  final EventType type) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Processing service change of reference %s type %s",
                                    reference, type));
        }
        for (ListenerRegistration reg : listeners) {
            if (reg.query.match(reference)) {
                final ServiceListener listener = reg.listener;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.serviceChanged(new ServiceEvent(type,
                                                                     reference));
                        } catch (Throwable e) {
                            log.error(String.format("Error when notifying listener %s on reference %s type %s",
                                                    listener, reference, type),
                                      e);
                        }
                    }
                });
            }
        }
    }

    /**
     * @param id
     * @param state
     */
    protected void update(UUID id, byte[] state) {
        ServiceReferenceImpl reference = deserialize(id, state);
        services.put(id, reference);
        serviceChanged(reference, EventType.MODIFIED);
    }
}
