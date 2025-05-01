package org.reso.service.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientContext {
    private static final Logger LOG = LoggerFactory.getLogger(ClientContext.class);
    private static final ThreadLocal<String> currentClient = new ThreadLocal<>();

    private ClientContext() {
    }

    public static void setCurrentClient(String clientId) {
        LOG.debug("Setting current client to: {}", clientId);
        currentClient.set(clientId);
    }

    public static String getCurrentClient() {
        return currentClient.get();
    }

    public static void clear() {
        LOG.debug("Clearing current client");
        currentClient.remove();
    }
} 