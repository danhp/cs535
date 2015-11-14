package socs.network.node;


import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientWorker implements Runnable {

    private Socket clientSocket;
    private RouterDescription serverRd;
    private Link newLink;

    public ClientWorker(Socket clientSocket, RouterDescription serverRd, Link newLink) {
        this.clientSocket = clientSocket;
        this.serverRd = serverRd;
        this.newLink = newLink;
    }

    public void run() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

            SOSPFPacket broadcastPacket = new SOSPFPacket();
            broadcastPacket.srcIP = serverRd.simulatedIPAddress;
            broadcastPacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
            broadcastPacket.neighborID = serverRd.simulatedIPAddress;
            broadcastPacket.srcProcessIP = serverRd.processIPAddress;
            broadcastPacket.routerID = serverRd.simulatedIPAddress;
            broadcastPacket.weight = this.newLink.weight;
            broadcastPacket.sospfType = 0;
            broadcastPacket.srcProcessPort = serverRd.processPortNumber;

            output.writeObject(broadcastPacket);

            while (true) {
                SOSPFPacket connectionPacket = (SOSPFPacket) input.readObject();

                //if HELLO
                if (connectionPacket.sospfType == 0) {
                    //Update link information
                    RouterDescription remoteRouter = newLink.router2;
                    remoteRouter.simulatedIPAddress = connectionPacket.srcIP;
                    remoteRouter.processIPAddress = connectionPacket.srcProcessIP;
                    remoteRouter.processPortNumber = connectionPacket.srcProcessPort;
                    remoteRouter.status = RouterStatus.TWO_WAY;

                    //Print out connection
                    System.out.println("Received HELLO from " + newLink.router2.simulatedIPAddress + " : ");
                    System.out.println(" Set " + newLink.router2.simulatedIPAddress + " state to " + newLink.router2.status);

                    // Add the link info to the database
                    Router.addToDatabase(this.newLink);

                    SOSPFPacket responsePacket = new SOSPFPacket();
                    responsePacket.srcIP = serverRd.simulatedIPAddress;
                    responsePacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
                    responsePacket.neighborID = serverRd.simulatedIPAddress;
                    responsePacket.srcProcessIP = serverRd.processIPAddress;
                    responsePacket.routerID = serverRd.simulatedIPAddress;
                    responsePacket.weight = this.newLink.weight;
                    responsePacket.sospfType = 0;
                    responsePacket.srcProcessPort = serverRd.processPortNumber;
                    responsePacket.lsaArray = Router.lsd.toVector();

                    output.writeObject(responsePacket);

                    // save the output stream for later
                    Router.outputs.put(remoteRouter.simulatedIPAddress, output);

                    // Create an update listener
                    Router.createUpdateListener(input, remoteRouter.simulatedIPAddress);
                    return;
                }
            }

        } catch (IOException ex) {
            if (this.newLink.router2.simulatedIPAddress == null) return;
            if (!Router.ports.contains(this.newLink)) return;

            System.out.println("Lost connection to: " + this.newLink.router2.simulatedIPAddress);
        } catch (Exception ex) {
            return;
        }

    }
}
