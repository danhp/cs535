package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLSyntaxErrorException;
import java.util.Vector;

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

                        // Try to add the new connection.
                        boolean success = Router.addLink(link);
                        if (!success){
                            serviceSocket.close();
                            continue;
                        }

                        // Add link to database
                        Router.addToDatabase(this.link);

                        Router.updateDatabase(responsePacket.lsaArray);

                        // Save the output stream for later.
                        Router.outputs.add(output);

                        // Create an update listener.
                        Router.createUpdateListener(input);

                        // Send first update message
                        Router.triggerUpdateAdd();
                    }

                    System.out.println("Received HELLO from " + link.router2.simulatedIPAddress + " : ");
                    System.out.println("Set " + link.router2.simulatedIPAddress + " state to " + link.router2.status);

                    if (this.link.router2.status == RouterStatus.TWO_WAY) return;
                }
            }
        } catch(IOException ex) {
            System.out.println(ex);
        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
        } catch (Exception ex) {
            return;
        }
    }
}
