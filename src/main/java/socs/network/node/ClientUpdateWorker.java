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
public class ClientUpdateWorker implements Runnable {
    private ObjectOutputStream output;
    private RouterDescription rd;

    public ClientUpdateWorker(ObjectOutputStream output, RouterDescription rd) {
        this.output = output;
        this.rd = rd;
    }

    public void run() {
       try {
           // Update packet
           SOSPFPacket updatePacket = new SOSPFPacket();
           updatePacket.srcIP = rd.simulatedIPAddress;
           updatePacket.sospfType = 1;
           updatePacket.lsaArray = Router.lsd.toVector();

           output.writeObject(updatePacket);
           output.reset();
           return;
       } catch (IOException ex) {
           System.out.println(ex);
       }
    }
}
