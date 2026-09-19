package com.lukaswhite.pos.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything the LoadBalancer knows about one registered ServerNode.
 *
 * This is intentionally a plain mutable object rather than a Java
 * "record": its fields change constantly (load and last-heartbeat time
 * update every few seconds, alive flips false if it goes quiet), and it
 * lives inside a ConcurrentHashMap that multiple threads touch at once
 * (the node's own registration handler thread, the heartbeat-monitor
 * background thread, and whichever thread is currently handling a
 * client's "assign me a server" request). AtomicInteger + volatile
 * fields keep those reads/writes safe without needing a lock.
 */
public class NodeInfo {

    private final String host;
    private final int port;

    private final AtomicInteger currentLoad = new AtomicInteger(0);
    private volatile long lastHeartbeatMillis;
    private volatile boolean alive;

    public NodeInfo(String host, int port) {
        this.host = host;
        this.port = port;
        touch(); // a brand-new node counts as "just heard from"
    }

    /** Call this every time we hear from the node (registration or heartbeat). */
    public void touch() {
        lastHeartbeatMillis = System.currentTimeMillis();
        alive = true;
    }

    public void setLoad(int load) {
        currentLoad.set(load);
    }

    public int getLoad() {
        return currentLoad.get();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /** "host:port" - used as the map key and as the string handed to clients. */
    public String getId() {
        return host + ":" + port;
    }

    public boolean isAlive() {
        return alive;
    }

    public void markDead() {
        alive = false;
    }

    public long getLastHeartbeatMillis() {
        return lastHeartbeatMillis;
    }

    @Override
    public String toString() {
        return String.format("%s (load=%d, alive=%s)", getId(), getLoad(), alive);
    }
}
