
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
		
		timer.schedule(new TimerTask(){
		
			@Override
			public void run() {
				broadcastMinCost();
			}
		}, updateInterval*5, updateInterval);
		
		// to be completed
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

	private int getDistance(int src, int dest) {
		return myRtnTables[src].getMinCost()[dest];
	}

	

	private void processROUTE(DvrPacket dvr) {
		//insert the new dVect
		if (dvr.sourceid == DvrPacket.SERVER) {
			myRtnTables[routerId] = new RtnTable(dvr.mincost, new int[dvr.mincost.length]);
		} else {
			myRtnTables[dvr.sourceid] = new RtnTable(dvr.mincost, new int[dvr.mincost.length]);
		}

		//We can't fully update the table, if entries are missing.
		boolean routingTableIsFull = true;
		for (RtnTable tbl : myRtnTables) {
			if (tbl == null) {
				routingTableIsFull = false;
				break;
			}
		}

		//do bellman-ford when we have all the tables
		int[] newMinCost = new int[myRtnTables.length];
		int[] newNextHop = new int[myRtnTables.length];
		if (routingTableIsFull) {
			
			int[] oldTableRef = myRtnTables[routerId].getMinCost();
			int[] oldDV = Arrays.copyOf(oldTableRef, oldTableRef.length);
			
			System.out.println("Tbl Full");
			for (int r = 0; r < myRtnTables.length; r++) {
				if (r == routerId) {
					newMinCost[r] = 0;
					newNextHop[r] = r;
					continue;
				}
				int minCost = Integer.MAX_VALUE, minNext = Integer.MAX_VALUE;
				for (int n = 0; n < myRtnTables.length; n++) {
					if (n == routerId)
						continue;
					int cin = getDistance(routerId, n); // c(x,y)
					int dnr = getDistance(n, r); //Dy(z);
					if (dnr > 900 || cin > 900)
						continue;
					int candidate = cin + dnr;
					if (candidate < minCost) {
						minCost = candidate;
						minNext = n;
					}
				}
				newMinCost[r] = minCost;
				newNextHop[r] = minNext;
			}
			myRtnTables[routerId] = new RtnTable(newMinCost, newNextHop);
			
			//forward my new table only if it changed
			if (!Arrays.equals(oldDV, newMinCost))
				broadcastMinCost();
		}
		


	}

	private void broadcastMinCost() {
		try {
			// send my min cost to all neighbors
			int[] myMinCost = myRtnTables[routerId].getMinCost();
			for (int i = 0; i < myMinCost.length; i++) {
				if (myRtnTables[routerId].getMinCost()[i] > 900) continue;
				if (i != routerId) {
					
					toServer.writeObject(new DvrPacket(routerId, i, DvrPacket.ROUTE, myMinCost));
				}
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
		
		int[] a = {1,2,3};
		int[] b = {1,2,3};
		if (Arrays.equals(a, b)) System.out.println("SAME");
		else System.out.println("DIFF");
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
