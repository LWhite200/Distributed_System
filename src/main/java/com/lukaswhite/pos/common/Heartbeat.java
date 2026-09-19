package com.lukaswhite.pos.common;

import java.io.Serializable;

/**
 * A tiny "I'm still alive, and here's how busy I am" message that a
 * ServerNode sends the LoadBalancer every few seconds, on the same
 * connection it registered with.
 *
 * currentLoad is just the number of clients that node is actively
 * serving right now. The load balancer uses this to pick the
 * least-busy alive node whenever a new client asks to be assigned one
 * ("least connections" load balancing).
 */
public class Heartbeat implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int currentLoad;

    public Heartbeat(int currentLoad) {
        this.currentLoad = currentLoad;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }
}
