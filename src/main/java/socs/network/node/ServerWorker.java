package socs.network.node;


import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ServerWorker implements Runnable {

    private Socket serviceSocket;
    private RouterDescription serverRd;
    private Link newLink;

    public ServerWorker(Socket serviceSocket, RouterDescription serverRd, Link newLink) {
        this.serviceSocket = serviceSocket;
        this.serverRd = serverRd;
        this.newLink = newLink;
    }

    public void run() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(serviceSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serviceSocket.getInputStream());

            SOSPFPacket broadcastPacket = new SOSPFPacket();
            broadcastPacket.srcIP = serverRd.simulatedIPAddress;
            broadcastPacket.dstIP = serviceSocket.getRemoteSocketAddress().toString();
            broadcastPacket.neighborID = serverRd.simulatedIPAddress;
            broadcastPacket.srcProcessIP = serverRd.processIPAddress;
            broadcastPacket.routerID = serverRd.simulatedIPAddress;
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

                    output.writeObject(broadcastPacket);
                }
            }

        } catch (IOException ex) {
            if (this.newLink.router2.simulatedIPAddress == null) return;
            if (!Router.ports.contains(this.newLink)) return;

            System.out.println("Lost connection to: " + this.newLink.router2.simulatedIPAddress);
            Router.ports.remove(this.newLink);
        } catch (Exception ex) {
            System.out.println(ex);
        }

    }
}
