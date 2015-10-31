package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

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

        //A* dijkstras algorithm
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

        Node current;
        while (!queue.isEmpty()) {
            current = queue.remove();

            Iterator<LinkDescription> it = _store.get(current.nodeId).links.iterator();
            while (it.hasNext()) {
                LinkDescription next = it.next();
                int nCost = costSoFar.get(next.linkID) + next.tosMetrics;

                if (!seenSoFar.get(next.linkID)) {
                    //not linkID sucks
                    queue.add(new Node(nCost, next.linkID));
                    costSoFar.put(next.linkID, nCost);
                    cameFrom.put(current.nodeId, next.linkID);
                }

                seenSoFar.put(current.nodeId, true);
                if (current.nodeId.equals(destinationIP)) {
                    //print out path
                    ArrayList<String> path = new ArrayList<String>();
                    path.add(destinationIP);

                    String curName = current.nodeId;
                    while (!curName.equals(rd.simulatedIPAddress)) {
                        curName = cameFrom.get(current.nodeId);
                        path.add(curName);
                    }

                    Collections.reverse(path);

                    return formatPath(path);
                }
            }
        }
        return "";
    }

    private String formatPath(ArrayList<String> path) {
        if (path.size() < 1) return "";

        String ret = "";
        String prev, cur;
        int weight = 0;

        for (int i = 1; i < path.size(); i++) {
            prev = path.get(i-1);
            cur = path.get(i);
            weight = _store.get(cur).links

            ret += prev + " -> (" + weight + ") ";
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
