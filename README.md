# Distributed POS System

This combines your two CIS 357 projects (the JavaFX + SQL point-of-sale
client/server, and the standalone heartbeat client/server demo) into one
system: a JavaFX client, a SQL-backed server that can run as **multiple
nodes** on one machine or across several, a **load balancer** that spreads
clients across whichever nodes are alive, and **heartbeat detection** so
the load balancer never sends a client to a dead node.

Heartbeating is no longer a separate demo — it's baked into how every
`ServerNode` talks to the `LoadBalancer`, so the old `HeartbeatClient`/
`HeartbeatServer` files aren't needed anymore and aren't part of this
project.

## How it fits together

```
                     ┌─────────────────────┐
   ServerNode A ───► │                      │
   (registers +      │     LoadBalancer     │ ◄─── Client asks:
    heartbeats)      │                      │      "who do I talk to?"
                      │  :9000 registration  │
   ServerNode B ───►  │  :9100 client-assign │
   (registers +      │                      │
    heartbeats)      └──────────┬───────────┘
                                 │ replies "host:port"
                                 │ of least-busy alive node
                                 ▼
                      Client connects DIRECTLY to that node
                      and does all item lookups there.
```

* **`ServerNode`** — one worker. Listens for Clients on its own port and
  answers item lookups against the SQLite database (`ClientHandler`,
  `DatabaseManager`). At the same time, it registers itself with the
  `LoadBalancer` and sends a `Heartbeat` (its current number of connected
  clients) every 4 seconds.
* **`LoadBalancer`** — the one address everything else needs to know. Keeps
  a live list of registered nodes (`NodeInfo`), marks a node dead if its
  heartbeats stop (`NodeRegistrationHandler` + a background monitor
  thread in `LoadBalancer`), and — when a `Client` asks — hands back the
  address of whichever **alive node currently has the fewest active
  clients** (`ClientAssignmentHandler`). This is the "node-balancing"
  part: least-connections load balancing.
* **`Client`** — same JavaFX screens as your original homework. The only
  change is *what* it connects to first: instead of typing in a specific
  server's IP, you type in the **load balancer's** IP. The client asks
  the load balancer for an assignment, then opens a normal, direct
  connection to whichever node it was given and everything else (item
  code, quantity, receipt, tax) works exactly like before.

Package layout:

```
com.lukaswhite.pos.common          ProductSpec, Registration, Heartbeat
com.lukaswhite.pos.server          ServerNode, ClientHandler, DatabaseManager
com.lukaswhite.pos.loadbalancer    LoadBalancer, NodeInfo,
                                    NodeRegistrationHandler, ClientAssignmentHandler
com.lukaswhite.pos.client          Client (JavaFX GUI)
```

## Requirements

* **Java 17 or newer** (JavaFX 21 needs 17+; if you're stuck on an older
  JDK, see the comment at the top of `pom.xml` for how to drop the
  versions down).
* **Maven**, to pull in JavaFX and the SQLite JDBC driver automatically.
  If you don't have Maven, see "Running without Maven" below.

I already test-ran every piece of this (registration, heartbeats, a node
timing out, least-connections assignment, and real item lookups against
your `items.db`) in a Linux sandbox before handing it to you, so the
logic itself is solid — the steps below are about getting it running on
*your* machine(s).

## Building

From the project root (where `pom.xml` is):

```
mvn clean package
```

This downloads JavaFX + the SQLite driver and compiles everything. It
also produces a single "shaded" jar with all dependencies bundled, at
`target/distributed-pos-1.0.0-shaded.jar`, in case you'd rather run with
plain `java -jar` than Maven on some machines (e.g. a second computer
that doesn't have Maven set up).

## Running — all on one machine (easiest way to test)

Open **three terminals** in the project root.

**Terminal 1 — the load balancer:**
```
mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.loadbalancer.LoadBalancer"
```

**Terminal 2 — a server node** (client port 8001):
```
mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.server.ServerNode" -Dexec.args="127.0.0.1 8001"
```

**Terminal 3 — a second server node**, so you can actually see load
balancing happen (client port 8002 — must differ from the first node
since they're on the same machine):
```
mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.server.ServerNode" -Dexec.args="127.0.0.1 8002"
```

**Then start the client** (a 4th terminal, or your IDE's run button):
```
mvn javafx:run
```
In the "Load Balancer IP Address" box, type `127.0.0.1` and hit Connect.
Launch a couple of client instances and you should see, in the
load-balancer terminal, `[client-assign] Assigned client to ...`
alternating between `:8001` and `:8002` as it spreads the load.

## Running across multiple physical machines

1. Pick one machine to run the `LoadBalancer` on and note its IP address
   (e.g. `192.168.1.10`). Run it there with no arguments:
   ```
   mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.loadbalancer.LoadBalancer"
   ```
2. On each machine that should act as a server node, run:
   ```
   mvn exec:java -Dexec.mainClass="com.lukaswhite.pos.server.ServerNode" -Dexec.args="192.168.1.10"
   ```
   (replace `192.168.1.10` with the load balancer's real IP). That node
   will auto-detect and advertise its own IP; if auto-detection picks the
   wrong network interface on a multi-homed machine, override it:
   `-Dexec.args="192.168.1.10 8001 192.168.1.20"`.
3. On each client machine, run `mvn javafx:run` and enter the load
   balancer's IP (`192.168.1.10`) when prompted.

Make sure the relevant ports are reachable between machines: **9000**
and **9100** on the load-balancer machine, and whichever port each node
is listening on (default **8001**) on the node machines.

## Running without Maven (plain `javac`/`java`)

If you'd rather not use Maven at all, `mvn clean package` still gives you
the easiest path: it downloads the jars for you once, and after that you
can run everything with plain `java -cp ...` using the jars Maven put in
your local repo (`~/.m2/repository`). If you truly want zero Maven, you'll
need to download `javafx-sdk` and `sqlite-jdbc-<version>.jar` yourself and
point `javac -cp` / `java -cp` at them directly — ask if you want the
exact commands for that route.

## Tuning

A few constants you might want to tweak while experimenting:

* `LoadBalancer.HEARTBEAT_TIMEOUT_MS` (10s) — how long a node can go
  quiet before it's declared dead.
* `ServerNode.HEARTBEAT_INTERVAL_MS` (4s) — how often a node reports in.
  Keep this comfortably shorter than the timeout above.
* The load-balancing strategy itself lives in
  `ClientAssignmentHandler.run()` — it's currently "fewest active
  clients wins"; that's the one line to change if you want to try
  round-robin or something else instead.

## What changed from your original files

* `Server.java` → `ServerNode.java` + `DatabaseManager.java` (the jar
  resource extraction is now its own class) + the new registration/
  heartbeat thread.
* `ClientHandler.java` — same logic, plus it now increments/decrements a
  shared counter so its `ServerNode` knows its own current load.
* `Client.java` — same three screens, but `connectToServer(ip)` became a
  two-step `connectToAssignedNode(ip)`: ask the load balancer, then
  connect to whoever it names.
* `ProductSpec.java` — unchanged except for the package line and a
  `serialVersionUID`.
* `HeartbeatClient.java` / `HeartbeatServer.java` — retired; the idea
  lives on inside `ServerNode`'s heartbeat-sender thread and the load
  balancer's `NodeRegistrationHandler`/monitor thread.
* New: `Registration.java`, `Heartbeat.java` (tiny messages nodes send
  the load balancer), `NodeInfo.java`, `NodeRegistrationHandler.java`,
  `ClientAssignmentHandler.java`, `LoadBalancer.java`.
