package socs.network.node;

import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLSyntaxErrorException;

public class ServerWorker implements Runnable {

    private Socket serviceSocket;
    private RouterDescription rd;
    private Link link;

    public ServerWorker(Socket clientSocket, RouterDescription rd, Link link) {
        this.serviceSocket = clientSocket;
        this.rd = rd;
        this.link = link;
    }

    public void run() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(serviceSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serviceSocket.getInputStream());

            while (true) {
                SOSPFPacket responsePacket = (SOSPFPacket)input.readObject();

                //If response = HELLO
                if (responsePacket.sospfType == 0) {
                    if (link.router2.status == null) {

                        this.link.router2.simulatedIPAddress = responsePacket.srcIP;
                        this.link.router2.processPortNumber = responsePacket.srcProcessPort;
                        this.link.router2.status = RouterStatus.INIT;
                        this.link.weight = responsePacket.weight;

                        //Client response -> Server
                        SOSPFPacket returnPacket = new SOSPFPacket();
                        returnPacket.srcIP = rd.simulatedIPAddress;
                        returnPacket.srcProcessIP = rd.processIPAddress;
                        returnPacket.srcProcessPort = rd.processPortNumber;
                        returnPacket.dstIP = serviceSocket.getRemoteSocketAddress().toString();
                        returnPacket.sospfType = 0;
                        returnPacket.routerID = rd.simulatedIPAddress;
                        returnPacket.neighborID = rd.simulatedIPAddress;

                        output.writeObject(returnPacket);
                    } else {

                        //otherwise has been INIT and want to finalize connection
                        this.link.router2.status = RouterStatus.TWO_WAY;

                        // replace the information in the ports list.
                        for (Link l : Router.ports) {
                            // Incomplete link were tagged with -1
                            if (l.weight < 0 ) {
                                l = this.link;
                                break;
                            }
                        }

                        // Add link to database
                        Router.addToDatabase(this.link);

                        // Trigger an update to add.
                        Router.triggerUpdateAdd();
                    }

                    System.out.println("Received HELLO from " + link.router2.simulatedIPAddress + " : ");
                    System.out.println("Set " + link.router2.simulatedIPAddress + " state to " + link.router2.status);
                }
            }
        } catch(IOException ex) {
            if (this.link.router2.simulatedIPAddress == null ) return;
            System.out.println("Lost connection to: " + this.link.router2.simulatedIPAddress);
            Router.ports.remove(this.link);

            // Trigger an update to remove.
            Router.triggerUpdateRemove(this.link.router2.simulatedIPAddress);

        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}
