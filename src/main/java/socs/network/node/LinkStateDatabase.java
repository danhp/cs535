package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.lang.reflect.Array;
import java.util.*;

public class LinkStateDatabase {

    //linkID => LSAInstance
    HashMap<String, LSA> _store = new HashMap<String, LSA>();

    private RouterDescription rd = null;

    public LinkStateDatabase(RouterDescription routerDescription) {
        this.rd = routerDescription;
        LSA l = initLinkStateDatabase();
        _store.put(l.linkStateID, l);
    }

    /**
     * output the shortest path from this router to the destination with the given IP address
     */
    String getShortestPath(String destinationIP) {
        if (destinationIP.equals(rd.simulatedIPAddress)) return "";

        PriorityQueue<Node> queue = new PriorityQueue<Node>(_store.size(), new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                if (o1.priority < o2.priority) return 0;
                else return 1;
            }
        });

        Node start = new Node(0, rd.simulatedIPAddress);

        HashMap<String, String> cameFrom = new HashMap<String, String>();
        HashMap<String, Integer> costSoFar = new HashMap<String, Integer>();
        HashMap<String, Boolean> seenSoFar = new HashMap<String, Boolean>();

        seenSoFar.put(rd.simulatedIPAddress, true);
        costSoFar.put(start.nodeId, 0);
        queue.add(start);

        Node current;
        while (!queue.isEmpty()) {
            current = queue.remove();

            for (LinkDescription l : _store.get(current.nodeId).links) {

                // weigth of 0 is the same router we already saw
                if (l.tosMetrics == 0) continue;
                int newCost = costSoFar.get(current.nodeId) + l.tosMetrics;

                if (!seenSoFar.containsKey(l.linkID)){
                    queue.add(new Node(newCost, l.linkID));
                    costSoFar.put(l.linkID, newCost);
                    cameFrom.put(l.linkID, current.nodeId);
                }
                seenSoFar.put(current.nodeId, true);

                if (l.linkID.equals(destinationIP)) {
                    System.out.println("Path Found");
                    ArrayList<String> path = new ArrayList<String>();
                    path.add(destinationIP);

                    String currentName = destinationIP;
                    while(!currentName.equals(rd.simulatedIPAddress)) {
                        currentName = cameFrom.get(l.linkID);
                        path.add(currentName);
                    }

                    Collections.reverse(path);

                    return formatPath(path);
                }
            }
        }
        return "";
    }

    private String formatPath(ArrayList<String> path) {
        if (path.size() <= 1) return "";
        String ret = path.get(0);

        String current, next;
        int weight;

        for (int i = 0; i < path.size() - 1; i++) {
            current = path.get(i);
            next = path.get(i+1);

            LinkedList<LinkDescription> links = _store.get(current).links;
            weight = 0;
            for (LinkDescription l : links) {
                if (l.linkID.equals(next)) weight = l.tosMetrics;
            }

            ret += " -> (" + weight + ") " + next;
        }

        return ret;
    }

    //initialize the linkstate database by adding an entry about the router itself
    private LSA initLinkStateDatabase() {
        LSA lsa = new LSA();
        lsa.linkStateID = this.rd.simulatedIPAddress;
        lsa.lsaSeqNumber = Integer.MIN_VALUE;

        LinkDescription ld = new LinkDescription();
        ld.linkID = this.rd.simulatedIPAddress;
        ld.portNum = -1;
        ld.tosMetrics = 0;

        lsa.links.add(ld);

        return lsa;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (LSA lsa: _store.values()) {
            sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
            for (LinkDescription ld : lsa.links) {
                sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                    append(ld.tosMetrics).append("\t");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

    class Node {
        public int priority;
        public String nodeId;

        public Node(int priority, String name) {
            this.priority = priority;
            this.nodeId = name;
        }

    }
