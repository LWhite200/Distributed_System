package com.lukaswhite.pos.common;

import java.io.Serializable;

/**
 * The very first message a ServerNode sends the LoadBalancer when it
 * connects: "here's who I am and where clients can reach me directly."
 *
 * host/port here describe the NODE's own client-facing address, not the
 * load balancer's address - the load balancer hands this address back
 * out to clients later so they can connect straight to the node.
 */
public class Registration implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;

    public Registration(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
