
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
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

			//TODO: Start timer on receipt of hello
			DvrPacket resp = null;
			do {
				resp = (DvrPacket) fromServer.readObject();
				processDvr(resp);
			} while (!sigQuit);
			//TODO: Stop timer on receipt of quit
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

	private void processROUTE(DvrPacket dvr){
		//We can't fully update the table, if entries are missing.
		boolean routingTableIsFull = true;
		for (RtnTable tbl : myRtnTables) {
			if (tbl == null) {
				routingTableIsFull = false;
				break;
			}
		}
		if (routingTableIsFull){
			//run Belman-Ford
		}

		//forward my new table
		broadcastMinCost();

	}

	private void broadcastMinCost() {
		try {
			// send my min cost to all neighbors
			int[] myMinCost = myRtnTables[routerId].getMinCost();
			for (int i = 0; i < myMinCost.length; i++) {
				if (i != routerId) {
					toServer.writeObject(new DvrPacket(routerId, i, routerId, myMinCost));
				}
			}
		} catch (Exception e) {
			System.err.println("oh no");
		}
	}

	/**
	 * A simple test driver
	 * 
	 */
	public static void main(String[] args) {
		// default parameters
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
