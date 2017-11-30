
import java.io.*;
import java.net.Socket;
import java.util.*;

import cpsc441.a4.shared.*;

/**
 * Router Class
 * 
 * This class implements the functionality of a router
 * when running the distance vector routing algorithm.
 * 
 * The operation of the router is as follows:
 * 1. send/receive HELLO message
 * 2. while (!QUIT)
 *      receive ROUTE messages
 *      update mincost/nexthop/etc
 * 3. Cleanup and return
 * 
 *      
 * @author 	Majid Ghaderi
 * @version	3.0
 *
 */
public class Router {

	private Socket relayServer;
	private ObjectOutputStream toServer;
	private ObjectInputStream fromServer;
	private boolean sigQuit = false;
	private int routerId;
	private int updateInterval;
	private RtnTable[] myRtnTables;
	private Timer timer;
	private List<Integer> neighbors;

	/**
	 * Constructor to initialize the rouer instance 
	 * 
	 * @param routerId			Unique ID of the router starting at 0
	 * @param serverName		Name of the host running the network server
	 * @param serverPort		TCP port number of the network server
	 * @param updateInterval	Time interval for sending routing updates to neighboring routers (in milli-seconds)
	 */
	public Router(int routerId, String serverName, int serverPort, int updateInterval) {
		try {
			relayServer = new Socket(serverName, serverPort);
			toServer = new ObjectOutputStream(relayServer.getOutputStream());
			fromServer = new ObjectInputStream(relayServer.getInputStream());
		} catch (Exception e) {
			System.err.println("Host not found, exiting...");
			System.exit(1);
		}

		this.routerId = routerId;
		this.updateInterval = updateInterval;
		this.timer = new Timer();

		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				broadcastMinCost();
			}
		}, updateInterval * 5, updateInterval);

	}

	/**
	 * starts the router 
	 * 
	 * @return The forwarding table of the router
	 */
	public RtnTable start() {
		try {

			DvrPacket hello = new DvrPacket(routerId, DvrPacket.SERVER, DvrPacket.HELLO);
			toServer.writeObject(hello);

			//TODO: Start timer on receipt of HELLO
			DvrPacket resp = null;
			do {
				resp = (DvrPacket) fromServer.readObject();
				processDvr(resp);
			} while (!sigQuit);
			//TODO: Stop timer on receipt of QUIT
			relayServer.close();

		} catch (Exception e) {
			System.err.println(e);
		}
		return myRtnTables[routerId];
	}

	private void processDvr(DvrPacket dvr) {
		if (dvr.sourceid == DvrPacket.SERVER) {
			switch (dvr.type) {
			case DvrPacket.QUIT:
				timer.cancel();
				sigQuit = true;
				break;
			case DvrPacket.HELLO:
			case DvrPacket.ROUTE:
				myRtnTables = new RtnTable[dvr.getMinCost().length];
				processROUTE(dvr);
				break;
			default:
				break;
			}
		} else if (dvr.type == DvrPacket.ROUTE) {
			processROUTE(dvr);
		}

	}

	private Integer getDistance(int src, int dest) {
		try {
			return myRtnTables[src].getMinCost()[dest];
		} catch (Exception e) {
			return null; //If the value is not present, skip it
		}

	}

	private void processROUTE(DvrPacket dvr) {
		boolean newTable = false;
		//insert the new dVect
		if (dvr.sourceid == DvrPacket.SERVER) {
			newTable = true;
			int[] minCost = dvr.getMinCost();

			myRtnTables[routerId] = new RtnTable(minCost, new int[minCost.length]);
			//repopulate neighbors table
			neighbors = new ArrayList<>();
			for(int i = 0; i < minCost.length; i++){
				if (i != routerId && minCost[i] < 900)
					neighbors.add(i);
			}
		} else {
			myRtnTables[dvr.sourceid] = new RtnTable(dvr.mincost, new int[dvr.mincost.length]);
		}

		int[] oldTableRef = myRtnTables[routerId].getMinCost();
		int[] oldDV = Arrays.copyOf(oldTableRef, oldTableRef.length);

		int[] newMinCost = new int[myRtnTables.length];
		int[] newNextHop = new int[myRtnTables.length];

		for (int end = 0; end < myRtnTables.length; end++) {
			//If we are on our own node, or the dv is still unavailable, skip it;
			if (end == routerId) {
				newMinCost[end] = oldDV[end];
				newNextHop[end] = routerId;
				continue;
			}
			int minCost = oldDV[end], minNext = 0;
			for (int mid = 0; mid < myRtnTables.length; mid++) {
				if (mid == routerId || myRtnTables[mid] == null)
					continue;
				Integer cin = getDistance(routerId, mid); // c(x,y)
				Integer dnr = getDistance(mid, end); //Dy(z);
				if (cin != null && dnr != null) {
					int candidate = cin + dnr;
					if (candidate <= minCost) {
						minCost = candidate;
						minNext = mid;
					}
				}

			}
			newMinCost[end] = minCost;
			newNextHop[end] = minNext;
		}
		myRtnTables[routerId] = new RtnTable(newMinCost, newNextHop);
		//forward my new table only if it changed or if it was sent from the server
		if (!Arrays.equals(oldDV, newMinCost) || newTable)
			broadcastMinCost();

	}

	private void broadcastMinCost() {
		try {
			// send my min cost to all neighbors
			int[] myMinCost = myRtnTables[routerId].getMinCost();

			for (int neighbor : neighbors) {
				toServer.writeObject(new DvrPacket(routerId, neighbor, DvrPacket.ROUTE, myMinCost));
			}
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	/**
	 * A simple test driver
	 * 
	 */
	public static void main(String[] args) {
		// default parameters

		int[] a = { 1, 2, 3 };
		int[] b = { 1, 2, 3 };
		if (Arrays.equals(a, b))
			System.out.println("SAME");
		else
			System.out.println("DIFF");
		int routerId = 0;
		String serverName = "localhost";
		int serverPort = 8887;
		int updateInterval = 1000; //milli-seconds

		if (args.length == 4) {
			routerId = Integer.parseInt(args[0]);
			serverName = args[1];
			serverPort = Integer.parseInt(args[2]);
			updateInterval = Integer.parseInt(args[3]);
		} else {
			System.out.println("incorrect usage, using defaults.");
		}

		// print the parameters
		System.out.printf("starting Router #%d with parameters:\n", routerId);
		System.out.printf("Relay server host name: %s\n", serverName);
		System.out.printf("Relay server port number: %d\n", serverPort);
		System.out.printf("Routing update intwerval: %d (milli-seconds)\n", updateInterval);

		// start the router
		// the start() method blocks until the router receives a QUIT message
		Router router = new Router(routerId, serverName, serverPort, updateInterval);
		RtnTable rtn = router.start();
		System.out.println("Router terminated normally");

		// print the computed routing table
		System.out.println();
		System.out.println("Routing Table at Router #" + routerId);
		System.out.print(rtn.toString());
	}

}
