package socs.network.node;

import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;


public class Router {
    protected LinkStateDatabase lsd;

    RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    // Link[] ports = new Link[4];
    List<Link> ports = new ArrayList<Link>(4);

    public Router(Configuration config) {
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        lsd = new LinkStateDatabase(rd);
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
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * NOTE: this command should not trigger link database synchronization
     */
    private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
        if (this.ports.size() >= 4) {
            System.out.println(this.rd.simulatedIPAddress + " is at capacity.");
            return;
        }

        RouterDescription newRd = new RouterDescription();

        newRd.processIPAddress = processIP;
        newRd.processPortNumber = processPort;
        newRd.simulatedIPAddress = simulatedIP;
        newRd.status = RouterStatus.INIT;

        Link newLink = new Link(this.rd, newRd);
        ports.add(newLink);

        // TODO: Add info to LinkStateDatabase
    }

    /**
     * broadcast Hello to neighbors
     */
    private void processStart() {

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
            System.out.println("IP Address: " + l.router2.simulatedIPAddress);
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
