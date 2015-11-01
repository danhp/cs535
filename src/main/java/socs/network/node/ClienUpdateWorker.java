package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.SOSPFPacket;

import javax.jws.soap.SOAPBinding;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Iterator;
import java.util.Vector;

/**
 * Created by daniwa on 2015-10-31.
 */
public class ClienUpdateWorker implements Runnable {
    private Socket clientSocket;
    private RouterDescription rd;
    private Link link;

    public ClienUpdateWorker(Socket clientSocket, RouterDescription rd, Link link) {
        this.clientSocket = clientSocket;
        this.rd = rd;
        this.link = link;
    }

    public void run() {
       try {
           ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

           // Package database
           Vector<LSA> packed = new Vector<LSA>();
           for (LSA lsa: Router.lsd._store.values()) {
               packed.add(lsa);
           }

           // Update packet
           SOSPFPacket updatePacket = new SOSPFPacket();
           updatePacket.srcIP = rd.simulatedIPAddress;
           updatePacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
           updatePacket.neighborID = rd.simulatedIPAddress;
           updatePacket.srcProcessIP = rd.processIPAddress;
           updatePacket.srcProcessPort = rd.processPortNumber;
           updatePacket.sospfType = 1;
           updatePacket.lsaArray = packed;

           output.writeObject(updatePacket);
           return;
       } catch (IOException ex) {
           System.out.println(ex);
       }
    }
}
