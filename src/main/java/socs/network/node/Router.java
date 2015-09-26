package socs.network.node;

import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;


public class Router {
    protected LinkStateDatabase lsd;
    private boolean serverIsRunning;

    private ServerSocket serverSocket;


    RouterDescription rd = new RouterDescription();

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
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {

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

        Link newLink = new Link(this.rd, newRd);

        try {
            Socket socket = new Socket(newRd.processIPAddress, newRd.processPortNumber);
            new Thread(new ClientWorker(socket, rd, newLink)).start();

        } catch (IOException ex) {
            System.out.println(ex);
        }
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

    /**
     * broadcast Hello to neighbors
     */
    private void processStart() {
        if (serverIsRunning) {
            System.out.println("The server is already running");
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                serverIsRunning = true;

                try {
                    while (true) {
                        Socket serviceSocket = serverSocket.accept();

                        RouterDescription remote = new RouterDescription();
                        remote.status = RouterStatus.INIT;
                        Link newLink = new Link(rd, remote);

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
