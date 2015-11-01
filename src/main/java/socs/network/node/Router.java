package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;


public class Router {
    protected static LinkStateDatabase lsd;
    private boolean serverIsRunning;

    private ServerSocket serverSocket;


    public static RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    // Link[] ports = new Link[4];
    public static List<Link> ports = new ArrayList<Link>(4);

    public Router(Configuration config) {
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        lsd = new LinkStateDatabase(rd);
        serverIsRunning = false;

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
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Socket serviceSocket = serverSocket.accept();

                        RouterDescription remote = new RouterDescription();
                        remote.status = null;

                        // Tag link weight to -1 so we update it later
                        Link newLink = new Link(rd, remote,(short) -1);

                        // Try to add the new connection.
                        boolean success = Router.addLink(newLink);
                        if (!success){
                            serviceSocket.close();
                            continue;
                        }

                        //spawn thread for confirmation of accepted socket
                        new Thread(new ServerWorker(serviceSocket, rd, newLink)).start();
                    }
                } catch (IOException ex) {
                    System.out.println(ex);
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        }).start();
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {
        System.out.println(lsd);

        System.out.println(lsd.getShortestPath(destinationIP));
    }

    public static synchronized void triggerUpdateAdd() {
        System.out.println("Triggering Update");
        for (Link l: ports) {
            if (l == null) continue;

            try {
                Socket socket = new Socket("0.0.0.0", l.router2.processPortNumber);
                new Thread(new ClienUpdateWorker(socket, Router.rd, l)).start();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
    }

    public static synchronized void triggerUpdateRemove(String simulatedAdress) {

    }

    /**
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private void processDisconnect(short portNumber) {
        for (Link l : ports) {
            if (l.router2.processPortNumber == portNumber) {
                ports.remove(l);

                // TODO: Announce change.
            }
        }
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
    }

    public static synchronized boolean addLink(Link link) {
        //if ports are full, or ports already contains the attachment
        if (ports.size() >= 4) {
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

    public static synchronized void removeFromDatabase(Link link) {

    }

    /**
     * broadcast initial Hello to neighbors
     */
    private void processStart() {
        for (Link l : ports) {
            if (l == null) continue;

            try {
                Socket socket = new Socket(l.router2.processIPAddress, l.router2.processPortNumber);
                new Thread(new ClientWorker(socket, this.rd, l)).start();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * This command does trigger the link database synchronization
     */
    private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {

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
        // TODO: Announce this router is quitting.
        for (Link l : this.ports) {
            this.ports.remove(l);

        }

        System.out.println("Process has quit succesfully.");
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

                } else if (command.equals("connect ")) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
