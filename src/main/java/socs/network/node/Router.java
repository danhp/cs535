package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;


public class Router {
    public final long EXPIRE_DELAY = 10000;

    protected static LinkStateDatabase lsd;

    private ServerSocket serverSocket;

    public static RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    // Link[] ports = new Link[4];
    public static List<Link> ports = new ArrayList<Link>(4);
    private static List<Link> toAttach = new ArrayList<Link>(4);

    public static Map<String, ObjectOutputStream> outputs = new ConcurrentHashMap<String, ObjectOutputStream>(4);
    public static Map<String, Long> expireTimes = new ConcurrentHashMap<String, Long>();

    private static ExecutorService executorService = Executors.newCachedThreadPool();

    public Router(Configuration config) {
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        lsd = new LinkStateDatabase(rd);

        System.out.println("Router initialized with IP : " + rd.simulatedIPAddress);

        try {
            int n = (new Random()).nextInt(1000) + 5000;

            //Create & open new socket
            serverSocket = new ServerSocket(n);
            System.out.println("Local IP " + serverSocket.getLocalSocketAddress() + " Local Port: " + serverSocket.getLocalPort());

            rd.processIPAddress = serverSocket.getLocalSocketAddress().toString();
            rd.processPortNumber = (short) serverSocket.getLocalPort();

        } catch (IOException ex) {
            System.out.println(ex);
        } catch (Exception ex) {
            System.out.println(ex);
        }

        // Start the server
        Thread server = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Socket serviceSocket = serverSocket.accept();

                        RouterDescription remote = new RouterDescription();
                        remote.status = null;

                        // Tag link weight to -1 so we update it later
                        Link newLink = new Link(rd, remote,(short) -1);

                        //spawn thread for confirmation of accepted socket
                        new Thread(new ServerWorker(serviceSocket, rd, newLink)).start();
                    }
                } catch (IOException ex) {
                    System.out.println(ex);
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });
        executorService.execute(server);

        // Start the cleanup Thread.
        Thread cleanup = new Thread(new Runnable() {
            public void run() {
               try {
                   while (true) {
                       Thread.sleep(EXPIRE_DELAY);
                       System.out.println("Running cleanup");
                       cleanup();
                   }
               } catch (InterruptedException e) {
                   System.out.println(e);
               }
            }
        });
        executorService.execute(cleanup);
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {
        System.out.println(lsd.getShortestPath(destinationIP));
    }

    public static synchronized void triggerUpdateAdd() {
        for (ObjectOutputStream o : outputs.values()) {
            if (o == null) continue;

            // Update packet
            SOSPFPacket updatePacket = new SOSPFPacket();
            updatePacket.srcIP = rd.simulatedIPAddress;
            updatePacket.sospfType = 1;
            updatePacket.lsaArray = Router.lsd.toVector();

            try {
                o.reset();
                o.writeObject(updatePacket);
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    public static synchronized void createUpdateListener(final ObjectInputStream input, final String remoteIp, boolean createHeart) {
        expireTimes.put(remoteIp, System.currentTimeMillis());

        // The listener thread.
        Thread listener = new Thread(new Runnable() {
            public void run() {
                try {
                    SOSPFPacket updatePacket;
                    while (true) {
                        updatePacket = (SOSPFPacket) input.readObject();
                        updateDatabase(updatePacket.lsaArray);

                        System.out.println(updatePacket.sospfType);
                        switch (updatePacket.sospfType) {
                            case (short) 2: // Sender is disconnecting
                                System.out.println("Received a request to disconnect from: " + updatePacket.srcIP);

                                // Update local data
                                Router.disconnectIP(updatePacket.srcIP);

                                // End this thread
                                Router.triggerUpdateAdd();
                                return;
                            case (short) 3: // Received heartbeat
                                updatePacket.sospfType = 4;
                                synchronized (expireTimes) {
                                    System.out.println("rec 1");
                                    expireTimes.put(remoteIp, System.currentTimeMillis());
                                }
                                synchronized (outputs) {
                                    try {
                                        outputs.get(remoteIp).writeObject(updatePacket);
                                    } catch (IOException e) {
                                        System.out.println("Failed to respond to heartbeat");
                                        disconnectIP(remoteIp);
                                        triggerUpdateAdd();
                                    }
                                }
                                return;
                            case (short) 4: // Received heartbeat response
                                synchronized (expireTimes) {
                                    System.out.println("rec 2");
                                    expireTimes.put(remoteIp, System.currentTimeMillis());
                                }
                                return;
                            default: // General update
                                Router.triggerUpdateAdd();
                                return;
                        }
                    }

                } catch (IOException e) {
                    System.out.println("Lost connection to: " + remoteIp);
                    Router.disconnectIP(remoteIp);
                    Router.triggerUpdateAdd();
                    return;
                } catch (ClassNotFoundException e) {
                    System.out.println(e);
                }
            }
        });
        executorService.execute(listener);

        if (!createHeart) {
            // The heartbeat thread
            Thread heart = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(10000/3);
                            SOSPFPacket heartbeatPacket = new SOSPFPacket();
                            heartbeatPacket.srcIP = Router.rd.simulatedIPAddress;
                            heartbeatPacket.sospfType = 3;
                            synchronized (outputs) {
                                try {
                                    System.out.println("Sending heartbeat");
                                    ObjectOutputStream o = outputs.get(remoteIp);
                                    if (o == null) return;
                                    o.writeObject(heartbeatPacket);
                                    o.reset();
                                } catch (IOException e) {
                                    System.out.println("Failed to write heartbeat");
                                    disconnectIP(remoteIp);
                                    return;
                                }
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            });
            executorService.execute(heart);
        }
    }

    public static synchronized void disconnectIP(String remoteIp) {
        // Update local database
        LSA lsa = lsd._store.get(Router.rd.simulatedIPAddress);
        for (LinkDescription ld : lsa.links) {
            if (ld.linkID.equals(remoteIp)) {
                lsa.links.remove(ld);
                lsa.lsaSeqNumber++;
                break;
            }
        }

        // Remove communication channel
        outputs.remove(remoteIp);
        for (Link l: ports) {
            if (l.router2.simulatedIPAddress.equals(remoteIp)) {
                ports.remove(l);
                break;
            }
        }
    }

    public static synchronized boolean updateDatabase(Vector<LSA> v) {
        boolean alreadySeen = true;
        for (LSA lsa: v) {
            LSA inDatabase =  Router.lsd._store.get(lsa.linkStateID);
            if (inDatabase == null || lsa.lsaSeqNumber > inDatabase.lsaSeqNumber) {
                Router.lsd._store.put(lsa.linkStateID, lsa);
                alreadySeen = false;
            }
        }

        return (!alreadySeen);
    }

    /**
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private synchronized void processDisconnect(short portNumber) {
        if (portNumber >= ports.size()) return;
        Link l = ports.get(portNumber);
        if (l == null) return;

        System.out.println("Disconnecting from: " + l.router2.simulatedIPAddress);

        // Remove from local ports
        ports.remove(l);

        // Remove from local database
        LSA lsa = this.lsd._store.get(this.rd.simulatedIPAddress);
        for (LinkDescription ld : lsa.links) {
            if (ld.linkID.equals(l.router2.simulatedIPAddress)) {
                lsa.links.remove(ld);
                lsa.lsaSeqNumber++;
                break;
            }
        }

        // Update packet
        SOSPFPacket disconnectPacket = new SOSPFPacket();
        disconnectPacket.srcIP = rd.simulatedIPAddress;
        disconnectPacket.sospfType = 2;
        disconnectPacket.lsaArray = this.lsd.toVector();

        ObjectOutputStream o = this.outputs.get(l.router2.simulatedIPAddress);
        this.outputs.remove(l.router2.simulatedIPAddress);
        try{
            o.reset();
            o.writeObject(disconnectPacket);
        } catch(IOException e) {
            System.out.println(e);
        }

        triggerUpdateAdd();

        return;
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to identify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * NOTE: this command should not trigger link database synchronization
     */
    private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {

        RouterDescription newRd = new RouterDescription();
        newRd.processIPAddress = processIP;
        newRd.processPortNumber = processPort;
        newRd.simulatedIPAddress = simulatedIP;

        Link newLink = new Link(this.rd, newRd, weight);
        boolean success = this.addLink(newLink);
        if (success) {
            this.ports.remove(newLink);
            this.toAttach.add(newLink);
        }
    }

    public static synchronized boolean addLink(Link link) {
        //if ports are full, or ports already contains the attachment
        if (ports.size() + toAttach.size() >= 4) {
            System.out.println(link.router1.simulatedIPAddress + " is at capacity.");
            return false;
        }

        for (Link l : ports) {
            if (l.router2.processPortNumber != 0 && l.router2.processPortNumber == link.router2.processPortNumber) {
                System.out.println(link.router2.processPortNumber + " already exists in ports list.");
                return false;
            }
        }

        ports.add(link);

        return true;
    }

    public static synchronized void addToDatabase(Link link) {
        // Add to database
        LinkDescription ld = new LinkDescription();
        ld.linkID = link.router2.simulatedIPAddress;
        ld.portNum = link.router2.processPortNumber;
        ld.tosMetrics = link.weight;

        LSA lsa = lsd._store.get(link.router1.simulatedIPAddress);
        lsa.links.add(ld);
        lsa.lsaSeqNumber++;
    }

    /**
     * broadcast initial Hello to neighbors
     */
    private static synchronized void processStart() {
        for (Link l : toAttach) {
            if (l == null) continue;

            try {
                Router.ports.add(l);
                Socket socket = new Socket(l.router2.processIPAddress, l.router2.processPortNumber);
                new Thread(new ClientWorker(socket, Router.rd, l)).start();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }

        toAttach = new ArrayList<Link>(4);
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * This command does trigger the link database synchronization
     */
    private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {
        RouterDescription newRd = new RouterDescription();
        newRd.processIPAddress = processIP;
        newRd.processPortNumber = processPort;
        newRd.simulatedIPAddress = simulatedIP;

        Link newLink = new Link(this.rd, newRd, weight);
        boolean success = this.addLink(newLink);
        if (success) {
            try {
                Socket socket = new Socket(processIP, processPort);
                new Thread(new ClientWorker(socket, this.rd, newLink)).start();
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    /**
     * output the neighbors of the routers
     */
    private void processNeighbors() {
        System.out.println("Neighbors of " + this.rd.simulatedIPAddress + ":");

        for (Link l : this.ports) {
            if (l == null) continue;

            // By construction, self router is router1 in its own ports list.
            System.out.println("IP Address: " + l.router2.simulatedIPAddress + " Port: " + l.router2.processPortNumber);
        }
    }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {
        System.out.println("Process has quit succesfully.");
        return;
    }

    private synchronized void cleanup() {
        List<String> toKill = new ArrayList<String>();
        for (Map.Entry<String, Long> entry: expireTimes.entrySet()) {
            if (entry.getValue() + EXPIRE_DELAY < System.currentTimeMillis()) {
                System.out.println("Lost connection to: " + entry.getKey() + " (expired)");
                disconnectIP(entry.getKey());
                toKill.add(entry.getKey());
            }
        }
        if (!toKill.isEmpty()) {
            triggerUpdateAdd();
            for (String s : toKill) {
                expireTimes.remove(s);
            }
        }
    }

    public void terminal() {
        try {
            InputStreamReader isReader = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isReader);
            System.out.print(">> ");
            String command = br.readLine();
            while (true) {
                if (command.startsWith("detect ")) {
                    String[] cmdLine = command.split(" ");
                    processDetect(cmdLine[1]);

                } else if (command.startsWith("disconnect ")) {
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));

                } else if (command.startsWith("quit")) {
                    processQuit();
                    break;

                } else if (command.startsWith("attach ")) {
                    String[] cmdLine = command.split(" ");
                    processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.equals("start")) {
                    processStart();

                } else if (command.startsWith("connect ")) {
                    String[] cmdLine = command.split(" ");
                    processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));

                } else if (command.equals("neighbors")) {
                    //output neighbors
                    processNeighbors();

                } else {
                    //invalid command
                    System.out.println("Invalid command");
                }
                System.out.print(">> ");
                command = br.readLine();
            }
            isReader.close();
            br.close();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
