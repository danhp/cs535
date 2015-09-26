package socs.network.node;

import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientWorker implements Runnable {

    private Socket clientSocket;
    private RouterDescription rd;
    private Link link;

    public ClientWorker(Socket clientSocket, RouterDescription rd, Link link) {
        this.clientSocket = clientSocket;
        this.rd = rd;
        this.link = link;
    }

    public void run() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

            while (true) {
                SOSPFPacket responsePacket = (SOSPFPacket)input.readObject();

                //If response = HELLO
                if (responsePacket.sospfType == 0) {
                    if (link.router2.status == null) {
                        //add link
                        Router.ports.add(link);
                        this.link.router2.simulatedIPAddress = responsePacket.srcIP;
                        this.link.router2.status = RouterStatus.INIT;

                        //Client response -> Server
                        SOSPFPacket returnPacket = new SOSPFPacket();
                        returnPacket.srcIP = rd.simulatedIPAddress;
                        returnPacket.srcProcessIP = rd.processIPAddress;
                        returnPacket.srcProcessPort = rd.processPortNumber;
                        returnPacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
                        returnPacket.sospfType = 0;
                        returnPacket.routerID = rd.simulatedIPAddress;
                        returnPacket.neighborID = rd.simulatedIPAddress;

                        output.writeObject(returnPacket);
                    }
                    else {
                        //otherwise has been INIT and want to finalize connection
                        this.link.router2.status = RouterStatus.TWO_WAY;
                    }

                    System.out.println("Received HELLO from " + link.router2.simulatedIPAddress + " : ");
                    System.out.println("Set " + link.router2.simulatedIPAddress + " state to " + link.router2.status);
                }
            }
        } catch(IOException ex) {
//            System.out.println(ex);
            System.out.println("Lost connection to: " + this.link.router2.simulatedIPAddress);
            Router.ports.remove(this.link);
        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}
