/**
 * Network Analysis implementation for CS1501 Project 4
 * As specified in the assignment instructions, no JCL classes
 * are used besides I/O or ArrayList.
 * To account for textbook code dependencies, all required
 * textbook files were added into the repository. 
 * All textbook code used in the project is cited. 
 *
 * @author	Anastasia Mokhon
 */
package cs1501_p4;

import java.util.ArrayList;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

public class NetAnalysis {
	private final int COPPER_SPEED = 230000000; 			// propagation speed of copper
	private final int FIBER_SPEED = 200000000;				// propagation speed of optical fiber
	private int numOfVertices;								// keeps track of number of vertices
	private boolean copperOnly = true;						// specifies if all links of the network are copper
	private DijkstraAllPairsSP shortestPaths;				// stores the shortest paths found by Dijkstra algorithm
	public EdgeWeightedDigraph computerNetwork;				// directed graph used for most methods
	private EdgeWeightedGraph computerNetworkUndirected; 	// undirected graph needed for KruskalMST in lowestAvgLatST()

	// custom constructor
	public NetAnalysis(String filename) {
		File file = new File(filename);
		Scanner sc;
		try {
			sc = new Scanner(file);
		}
		catch (FileNotFoundException e) {
			System.out.println("File not found");
			return;
		}

		int count = 0;
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			if (count == 0) {
				// if this is the first line, record the number of vertices
				// and create the directed and undirected graphs
				int temp = Integer.parseInt(line);
				computerNetwork = new EdgeWeightedDigraph(temp);
				computerNetworkUndirected = new EdgeWeightedGraph(temp);
			}
			else {
				// record specifications of every edge
				String[] contents = line.split(" ");
				int vertexOne = Integer.parseInt(contents[0]);
				int vertexTwo = Integer.parseInt(contents[1]);
				String material = contents[2];
				int bandwidth = Integer.parseInt(contents[3]);
				int length = Integer.parseInt(contents[4]);

				// if there's at least one optical fiber connection, mark the copperOnly shortcut
				if (material.equals("optical")) copperOnly = false;

				// calls helper method
				double weight = calculateWeight(material, bandwidth, length);

				// record the edge in both directed and undirected graphs
				DirectedEdge edge1 = new DirectedEdge(vertexOne, vertexTwo, material, bandwidth, weight);
				computerNetwork.addEdge(edge1);
				DirectedEdge edge2 = new DirectedEdge(vertexTwo, vertexOne, material, bandwidth, weight);
				computerNetwork.addEdge(edge2);

				Edge undirectedEdge = new Edge(vertexOne, vertexTwo, material, bandwidth, weight);
				computerNetworkUndirected.addEdge(undirectedEdge);
			}
			count++;
		}
		// constructs all shortest paths of the directed graph
		shortestPaths = new DijkstraAllPairsSP(computerNetwork);

		// record number of vertices
		numOfVertices = computerNetwork.V();
	}

	// helper method that calculates travel time associated with a specific edge
	private double calculateWeight(String m, int b, int l) {
		// calculating speed as the sum of 
		// serialization delay: (packet size)/(bandwidth)
		// and link media delay: (link length)/(propagation speed)
		double result = -1.0;
		if (m.equals("copper")) {
			result = ((double) l/COPPER_SPEED) +  1/b;
		} 
		else if (m.equals("optical")) {
			result = ((double) l/FIBER_SPEED) + 1/b;
		}
		return result;
	}

	// finds the lowest latency path from vertex `u` to vertex `w` in the graph
	// returns null if no path exists
	public ArrayList<Integer> lowestLatencyPath(int u, int w) {
		// base case
		if (u < 0 || w < 0) return null;
		if (u == w) {
			ArrayList<Integer> ret = new ArrayList<Integer>();
			ret.add(u);
			return ret;
		}

		ArrayList<Integer> result = new ArrayList<Integer>();

		// refers to DijkstraAllPairsSP class to find the shortest path
		if (shortestPaths.hasPath(u, w)) {
			Iterable<DirectedEdge> p = shortestPaths.path(u, w);
			int count = 0;
			for (DirectedEdge e : p) {
				if (count == 0) {
					result.add(e.from());
				}
				result.add(e.to());
				count++;
			}
		}
		if (result.size() > 1) return result;
		else return null;
	}

	// finds the bandwidth available along a given path through the graph
	// throws `IllegalArgumentException` if the specified path is not valid
	public int bandwidthAlongPath(ArrayList<Integer> p) throws IllegalArgumentException {
		// base case
		if (p == null || p.size() < 2) throw new IllegalArgumentException("Invalid path");

		// converting ArrayList to array for traversal purposes
		int[] array = new int[p.size()];
    	int count = 0;
    	for (int num : p) {
    		array[count] = num;
    		count++; 
    	}

		Iterable<DirectedEdge> listOfEdges;
		int minBandwidth = Integer.MAX_VALUE;

		// traversing the directed graph to obtain bandwidth of every
		// edge that provided path contains
		for (int i = 0; i < array.length - 1; i++) {
			listOfEdges = computerNetwork.adj(array[i]);
			for (DirectedEdge e : listOfEdges) {
				if (e.to() == array[i + 1]) {
					if (e.getBandwidth() < minBandwidth) {
						minBandwidth = e.getBandwidth();
					}
				}
			}
		}
		if (minBandwidth == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid path");
		return minBandwidth;
	}

	// returns 'true' if the graph is connected considering only copper links
	// 'false' otherwise
	public boolean copperOnlyConnected() {
		// shortcut for the base case
		if (copperOnly) return true;

		boolean cConnected = true;
		Iterable<DirectedEdge> listOfEdges;
		boolean hasCopperConnection;

		// traverses the directed graph and checks if there is at least
		// one copper connection for every vertex 
		for (int i = 0; i < numOfVertices; i++) {
			listOfEdges = computerNetwork.adj(i);
			hasCopperConnection = false;
			for (DirectedEdge e : listOfEdges) {
				if (e.getMaterial().equals("copper")) {
					hasCopperConnection = true;
					break;
				}
			}

			// if the current vertex doesn't contain a single copper
			// connection, no need  to traverse further
			if (!hasCopperConnection) {
				cConnected = false;
				break;
			}
		}
		if (cConnected) return true;
		else return false;
	}

	// returns true if the graph would remain connected if any two vertices in
	// the graph would fail, 'false' otherwise
	public boolean connectedTwoVertFail() {
		// base case
		if (numOfVertices <= 3) return false;

		// traversing the directed graph and checking whether the graph would
		// remain connected if two vertices are removed for every i,j vertices pair 
		boolean remainsConnected = true;
		for (int i = 0; i < numOfVertices; i++) {
			for (int j = i + 1; j < numOfVertices; j++) {
				// calls helper method
				if (!twoVertHelper(i, j)) {
					remainsConnected = false;
					break;
				}
			}
		}
		return remainsConnected;
	}

	// helper method that checks if the connection for all vertices is maintained
	// if vertices i and j are removed
	private boolean twoVertHelper(int i, int j) {
		// base case
		if (i < 0 || j < 0) return false; 

		// stores all visited vertices in a boolean array
		boolean[] markedArray = new boolean[numOfVertices];
		boolean connected = false;
		markedArray[i] = true;
		markedArray[j] = true;

		int start = 0;
		if (start == i) start = 1;
		if (start == j) start = 2;
		markedArray[start] = true;
		Queue<Integer> q = new Queue<Integer>();
		q.enqueue(start);

		// traversing the directed graph and marking every possible 
		// vertex
		int count = 1;
		while (!q.isEmpty()) {
			int temp1 = q.dequeue();
			for (DirectedEdge e : computerNetwork.adj(temp1)) {
				int temp2 = e.to();
				if (!markedArray[temp2]) {
					markedArray[temp2] = true;
					count++;
					q.enqueue(temp2);
				}
			}
		}

		// if the number of visited vertices + the two that failed equals the
		// total number of vertices, return 'true', otherwise return 'false'
		if (count+2 == numOfVertices) connected = true;
		return connected;
	}

	// finds the lowest average latency spanning tree for the graph (MST)
	// returns an array of STE objects as specified in assignment instructions
	public ArrayList<STE> lowestAvgLatST() {
		ArrayList<STE> result = new ArrayList<STE>();

		// calls Kruskal algorithm on the undirected graph to find the MST
		KruskalMST kruskal = new KruskalMST(computerNetworkUndirected);
		Iterable<Edge> kruskalEdges = kruskal.edges();

		// recording the resulting Edge list into a STE ArrayList
		for (Edge e : kruskalEdges) {
			int vertexOne = e.either();
			int vertexTwo = e.other(vertexOne);
			result.add(new STE(vertexOne, vertexTwo));
		}
		return result;
	}
}
