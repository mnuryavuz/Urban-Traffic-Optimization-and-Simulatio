import java.util.*;

class Edge {
    int destination;
    int baseWeight;
    double congestionFactor;

    Edge(int destination, int baseWeight) {
        this.destination = destination;
        this.baseWeight = baseWeight;
        this.congestionFactor = 1.0;
    }

    public int getWeight() {
        return (int) (baseWeight * congestionFactor);
    }

    public void updateCongestion(double factor) {
        this.congestionFactor = factor;
    }
}

public class Graph {
    private final Map<Integer, List<Edge>> adjacencyList;
    private final Random rand = new Random();

    public Graph() {
        adjacencyList = new HashMap<>();
    }

    public void loadCityLayout(Map<Integer, double[]> positions, List<int[]> connections) {
        adjacencyList.clear();
        for (int id : positions.keySet()) {
            addIntersection(id);
        }
        for (int[] conn : connections) {
            addRoad(conn[0], conn[1], conn[2]);
        }
    }

    public void removeRoad(int src, int dest) {
        adjacencyList.get(src).removeIf(edge -> edge.destination == dest);
        adjacencyList.get(dest).removeIf(edge -> edge.destination == src);
        System.out.println("Road between " + src + " and " + dest + " removed.");
    }

    public void addIntersection(int node) {
        adjacencyList.putIfAbsent(node, new ArrayList<>());
    }

    public void addRoad(int src, int dest, int weight) {
        adjacencyList.putIfAbsent(src, new ArrayList<>());
        adjacencyList.putIfAbsent(dest, new ArrayList<>());
        if (!roadExists(src, dest)) {
            adjacencyList.get(src).add(new Edge(dest, weight));
            adjacencyList.get(dest).add(new Edge(src, weight));
        }
    }

    private boolean roadExists(int src, int dest) {
        return adjacencyList.getOrDefault(src, new ArrayList<>())
                .stream().anyMatch(e -> e.destination == dest);
    }

    public void updateTrafficConditions() {
        for (List<Edge> edges : adjacencyList.values()) {
            for (Edge edge : edges) {
                double newCongestionFactor = 1.0 + (rand.nextDouble() * 1.5);
                edge.updateCongestion(newCongestionFactor);
            }
        }
        System.out.println("Traffic conditions updated.");
    }

    public List<Integer> findShortestPath(int start, int end) {
        Map<Integer, Integer> prev = new HashMap<>();
        Map<Integer, Integer> distances = new HashMap<>();
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));

        pq.offer(new int[]{start, 0});
        distances.put(start, 0);

        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int node = current[0], cost = current[1];

            if (node == end) break;

            for (Edge edge : adjacencyList.getOrDefault(node, new ArrayList<>())) {
                int newDist = cost + edge.getWeight();
                if (!distances.containsKey(edge.destination) || newDist < distances.get(edge.destination)) {
                    distances.put(edge.destination, newDist);
                    prev.put(edge.destination, node);
                    pq.offer(new int[]{edge.destination, newDist});
                }
            }
        }

        List<Integer> path = new ArrayList<>();
        for (Integer at = end; at != null; at = prev.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        return path.isEmpty() || path.get(0) != start ? Collections.emptyList() : path;
    }

    public Map<Integer, List<Edge>> getGraph() {
        return adjacencyList;
    }

    public void redistributeTraffic() {
        for (List<Edge> edges : adjacencyList.values()) {
            for (Edge edge : edges) {
                if (edge.getWeight() > 20) {
                    edge.updateCongestion(1.0); // Reset to normal
                }
            }
        }
        System.out.println("Redistributed traffic: Heavy congestion eased.");
    }

    public int getLiveWeightBetween(int from, int to) {
        return adjacencyList.getOrDefault(from, new ArrayList<>()).stream()
            .filter(e -> e.destination == to)
            .findFirst()
            .map(Edge::getWeight)
            .orElse(0);
    }
}
