import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.*;
import javafx.scene.effect.Glow;

import java.util.*;

public class MainGUI extends Application {
    private final Pane graphPane = new Pane(); // only for nodes and roads
    private final Graph graph = new Graph();
    private final Map<Integer, Circle> nodes = new HashMap<>();
    private final Map<String, Line> roads = new HashMap<>();
    private final Map<String, Text> roadLabels = new HashMap<>();
    private Integer startNode = null;
    private Integer endNode = null;
    private final boolean[] placingMode = {false};
    private final boolean[] comparisonMode = {false};
    private final Map<String, Integer> snapshotWeights = new HashMap<>();
    private final Label travelTimeLabel = new Label("Total Travel Time: —");

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 600;

    @Override
    public void start(Stage primaryStage) {
        // Set up left control panel with simulation buttons
        VBox controlPanel = new VBox(15); // 15px vertical spacing
        controlPanel.setPrefWidth(200); // sidebar width
        controlPanel.setStyle("-fx-padding: 10; -fx-background-color: #D6DBDF;");

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        Button findPathButton = new Button("Find Shortest Path");
        Button removeRoadButton = new Button("Remove Road");
        Button emergencyButton = new Button("Emergency");
        Button placeIntersectionBtn = new Button("Place Mode: OFF");
        Button addRoadButton = new Button("Add Road");
        Button editRoadBtn = new Button("Edit Road");
        Button redistributeBtn = new Button("Redistribute Traffic");
        Button compareViewBtn = new Button("Comparison View: OFF");

        Label instructions = new Label(
            "Instructions:\n" +
            "• Click two intersections to select a path.\n" +
            "• Use buttons to add/edit roads or simulate.\n" +
            "• Use 'Place Mode' to add new intersections.\n" +
            "• Press ESC to reset selection."
        );
        instructions.setWrapText(true);
        instructions.setStyle("-fx-font-size: 11; -fx-padding: 5;");

        travelTimeLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        controlPanel.getChildren().addAll(
            instructions,
            travelTimeLabel,
            findPathButton, removeRoadButton, emergencyButton,
            placeIntersectionBtn, addRoadButton, editRoadBtn,
            redistributeBtn, compareViewBtn,
            bottomSpacer
        );

        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(controlPanel);
        mainLayout.setCenter(graphPane);

        Scene scene = new Scene(mainLayout, WINDOW_WIDTH, WINDOW_HEIGHT, Color.WHITE);
        primaryStage.setTitle("Traffic Network Simulation");
        primaryStage.setScene(scene);
        primaryStage.show();

        placeIntersectionsAndRoads();

        // === Button Actions ===
        // Button to compute and highlight shortest path between selected nodes
        findPathButton.setOnAction(e -> {
            if (startNode != null && endNode != null)
                highlightShortestPath(startNode, endNode);
        });

        // Button to remove an existing road between two selected nodes
        // and update the visualization to reflect the removal
        removeRoadButton.setOnAction(e -> {
            if (startNode != null && endNode != null) {
                removeRoad(startNode, endNode);
                updateRoadColors();
            }
        });

        // Button to simulate an emergency event:
        emergencyButton.setOnAction(e -> {
            if (startNode != null && endNode != null)
                simulateEmergency(startNode, endNode);
        });

        // Toggle intersection placement mode (free-click placement)
        placeIntersectionBtn.setOnAction(e -> {
            placingMode[0] = !placingMode[0];
            placeIntersectionBtn.setText("Place Mode: " + (placingMode[0] ? "ON" : "OFF"));
        });

        // Button to add a road between two selected intersections
        // Prompts user for a travel time value between 5 and 30
        addRoadButton.setOnAction(e -> {
            if (startNode != null && endNode != null && !startNode.equals(endNode)) {
                String key = startNode + "-" + endNode;
                String reverseKey = endNode + "-" + startNode;
                if (roads.containsKey(key) || roads.containsKey(reverseKey)) {
                    System.out.println("Road already exists.");
                    return;
                }

                // Dialog to input new road's travel time between two selected intersections
                TextInputDialog dialog = new TextInputDialog("10");
                dialog.setTitle("Add Road");
                dialog.setHeaderText("Travel time between " + startNode + " and " + endNode);
                dialog.setContentText("Enter travel time (5–30):");
                Optional<String> result = dialog.showAndWait();

                result.ifPresent(input -> {
                    try {
                        int weight = Integer.parseInt(input);
                        if (weight < 5 || weight > 30) {
                            System.out.println("Use 5–30");
                            return;
                        }

                        graph.addRoad(startNode, endNode, weight);
                        Map<Integer, double[]> pos = new HashMap<>();
                        for (var entry : nodes.entrySet())
                            pos.put(entry.getKey(), new double[]{entry.getValue().getCenterX(), entry.getValue().getCenterY()});
                        addRoad(startNode, endNode, weight, pos);
                        updateRoadColors();
                    } catch (Exception ex) {
                        System.out.println("Invalid input.");
                    }
                });
            } else {
                System.out.println("Select 2 different intersections first.");
            }
        });

        // Button to update the weight (travel time) of a road between two selected intersections
        // Prompts user for a new weight value within the 5–30 range
        editRoadBtn.setOnAction(e -> {
            if (startNode != null && endNode != null && !startNode.equals(endNode)) {
                TextInputDialog dialog = new TextInputDialog("10");
                dialog.setTitle("Edit Road");
                dialog.setHeaderText("Update travel time");
                dialog.setContentText("New travel time (5–30):");
                Optional<String> result = dialog.showAndWait();

                result.ifPresent(input -> {
                    try {
                        int newWeight = Integer.parseInt(input);
                        if (newWeight < 5 || newWeight > 30) {
                            System.out.println("Use range 5–30");
                            return;
                        }

                        // Update both directions of the undirected edge
                        for (Edge edge : graph.getGraph().get(startNode)) {
                            if (edge.destination == endNode) edge.baseWeight = newWeight;
                        }
                        for (Edge edge : graph.getGraph().get(endNode)) {
                            if (edge.destination == startNode) edge.baseWeight = newWeight;
                        }

                        // Update weight label on GUI
                        updateRoadColors();
                        if (roadLabels.containsKey(startNode + "-" + endNode))
                            roadLabels.get(startNode + "-" + endNode).setText(String.valueOf(newWeight));
                        if (roadLabels.containsKey(endNode + "-" + startNode))
                            roadLabels.get(endNode + "-" + startNode).setText(String.valueOf(newWeight));

                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid input.");
                    }
                });
            } else {
                System.out.println("Select two connected intersections first.");
            }
        });

        // Button to redistribute traffic:
        // - Resets edge congestion factors for roads with high weight
        // - Intended to simulate system-wide traffic balancing
        redistributeBtn.setOnAction(e -> {
            graph.redistributeTraffic();
            updateRoadColors();
        });

        // Button to toggle "Comparison View":
        // - ON: stores current weights as a snapshot
        // - OFF: compares current weights with snapshot and updates edge colors accordingly
        compareViewBtn.setOnAction(e -> {
            comparisonMode[0] = !comparisonMode[0];
            compareViewBtn.setText("Comparison View: " + (comparisonMode[0] ? "ON" : "OFF"));
            if (comparisonMode[0]) {
                // Save current weights
                snapshotWeights.clear();
                for (var entry : graph.getGraph().entrySet()) {
                    int src = entry.getKey();
                    for (Edge edge : entry.getValue()) {
                        snapshotWeights.put(src + "-" + edge.destination, edge.getWeight());
                    }
                }
            } else {
                // Compare with snapshot and color edges based on change
                for (String key : roads.keySet()) {
                    String[] parts = key.split("-");
                    int src = Integer.parseInt(parts[0]);
                    int dst = Integer.parseInt(parts[1]);
                    int before = snapshotWeights.getOrDefault(key, -1);
                    int after = graph.getGraph().get(src).stream()
                        .filter(edge -> edge.destination == dst).findFirst().map(Edge::getWeight).orElse(before);
                    if (after > before) roads.get(key).setStroke(Color.DARKRED);
                    else if (after < before) roads.get(key).setStroke(Color.DARKGREEN);
                    else roads.get(key).setStroke(Color.GRAY);
                }
            }
        });

        // ESCAPE key reset
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> resetNodeSelection();
            }
        });

        // Click to place intersection
        graphPane.setOnMouseClicked(e -> {
            if (placingMode[0]) {
                double x = e.getX();
                double y = e.getY();

                // Prevent placing node too close to any existing node (min distance: 25 pixels)
                for (Circle existing : nodes.values()) {
                    double dx = existing.getCenterX() - x;
                    double dy = existing.getCenterY() - y;
                    if (Math.sqrt(dx * dx + dy * dy) < 25) {
                        System.out.println("Too close to existing intersection.");
                        return;
                    }
                }

                int id = nodes.size() + 1;
                graph.addIntersection(id);
                addIntersection(id, x, y);
            }
        });

        // Timer: updates congestion every 20 seconds and refreshes road visuals
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(20), e -> {
            graph.updateTrafficConditions();
            updateRoadColors();
        }));
        t.setCycleCount(Timeline.INDEFINITE);
        t.play();
    }

    private void placeIntersectionsAndRoads() {
        Map<Integer, double[]> positions = new HashMap<>();
        List<int[]> connections = new ArrayList<>();

        int rows = 5, cols = 10, spacingX = 100, spacingY = 100;
        int[][] grid = new int[rows][cols];
        int id = 1;

        // Assign grid IDs
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                grid[r][c] = id++;

        // Set node positions (shifted to the right to avoid overlapping sidebar)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int nodeId = grid[r][c];
                double x = 50 + c * spacingX;  // offset to account for sidebar
                double y = 100 + r * spacingY;
                positions.put(nodeId, new double[]{x, y});
            }
        }

        // Create connections (horizontal and vertical)
        Random rand = new Random();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int current = grid[r][c];
                if (c < cols - 1)
                    connections.add(new int[]{current, grid[r][c + 1], rand.nextInt(10) + 5});
                if (r < rows - 1)
                    connections.add(new int[]{current, grid[r + 1][c], rand.nextInt(10) + 5});
            }
        }

        // Load into graph structure
        graph.loadCityLayout(positions, connections);

        // First add roads
        for (int[] conn : connections) {
            addRoad(conn[0], conn[1], conn[2], positions);
        }

        // Then add nodes (so they appear on top)
        for (Map.Entry<Integer, double[]> entry : positions.entrySet()) {
            addIntersection(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }

    private void addIntersection(int id, double x, double y) {
        Circle node = new Circle(x, y, 8, Color.BLUE);
        node.setStroke(Color.BLACK);
        node.setStrokeWidth(1);

        Tooltip.install(node, new Tooltip("Intersection " + id));

        node.setOnMouseEntered(e -> {
            node.setScaleX(1.3);
            node.setScaleY(1.3);
            node.setEffect(new Glow(0.5));
        });

        node.setOnMouseExited(e -> {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
            node.setEffect(null);
        });

        node.setOnMouseClicked(e -> handleNodeClick(id, e));

        nodes.put(id, node);
        graphPane.getChildren().add(node);
        node.toFront(); // Ensure node appears above roads
    }

    // Node click handler for selecting start and end nodes for pathfinding or road editing
    private void handleNodeClick(int nodeId, MouseEvent event) {
        if (placingMode[0]) return;

        if (startNode == null) {
            startNode = nodeId;
            Circle node = nodes.get(startNode);
            node.setFill(Color.ORANGE); // Highlight fill
            node.setStroke(Color.BLACK);
            node.setStrokeWidth(3);
            node.setEffect(new Glow(0.3));
        } else if (endNode == null) {
            if (nodeId == startNode) return;
            endNode = nodeId;
            Circle node = nodes.get(endNode);
            node.setFill(Color.RED); // Highlight fill
            node.setStroke(Color.BLACK);
            node.setStrokeWidth(3);
            node.setEffect(new Glow(0.5));
        } else {
            resetNodeSelection(); // reset previous
            startNode = nodeId;
            Circle node = nodes.get(startNode);
            node.setFill(Color.ORANGE);
            node.setStroke(Color.BLACK);
            node.setStrokeWidth(3);
            node.setEffect(new Glow(0.3));
        }
    }

    private void resetNodeSelection() {
        if (startNode != null && nodes.containsKey(startNode)) {
            Circle node = nodes.get(startNode);
            node.setFill(Color.BLUE);        // Reset fill
            node.setStroke(Color.BLACK);     // Reset stroke
            node.setStrokeWidth(1);          // Reset width
            node.setEffect(null);            // Remove glow
        }
        if (endNode != null && nodes.containsKey(endNode)) {
            Circle node = nodes.get(endNode);
            node.setFill(Color.BLUE);
            node.setStroke(Color.BLACK);
            node.setStrokeWidth(1);
            node.setEffect(null);
        }
        startNode = null;
        endNode = null;
    }

    private void addRoad(int src, int dest, int weight, Map<Integer, double[]> pos) {
        double[] a = pos.get(src), b = pos.get(dest);
        Line line = new Line(a[0], a[1], b[0], b[1]);
        updateRoadColor(line, weight);

        double mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
        Text label = new Text(mx + 5, my - 5, String.valueOf(weight)); // slight offset

        graphPane.getChildren().addAll(line, label);
        line.toBack();
        label.toBack();
        roads.put(src + "-" + dest, line);
        roads.put(dest + "-" + src, line);
        roadLabels.put(src + "-" + dest, label);
        roadLabels.put(dest + "-" + src, label);
    }

    private void updateRoadColor(Line road, int w) {
        if (w <= 8) road.setStroke(Color.GREEN);
        else if (w <= 15) road.setStroke(Color.YELLOW);
        else road.setStroke(Color.RED);
        road.setStrokeWidth(Math.min(6, 1.5 + w / 5.0));
    }

    private void updateRoadColors() {
        Set<String> seen = new HashSet<>(); // Track which edge we've already updated

        for (String key : roads.keySet()) {
            String[] pair = key.split("-");
            int src = Integer.parseInt(pair[0]);
            int dst = Integer.parseInt(pair[1]);

            // Avoid updating both src-dst and dst-src redundantly
            String undirectedKey = Math.min(src, dst) + "-" + Math.max(src, dst);
            if (seen.contains(undirectedKey)) continue;
            seen.add(undirectedKey);

            graph.getGraph().getOrDefault(src, new ArrayList<>()).stream()
                .filter(edge -> edge.destination == dst)
                .findFirst()
                .ifPresent(edge -> {
                    int liveWeight = edge.getWeight();

                    // Update visual line color
                    updateRoadColor(roads.get(key), liveWeight);

                    // Update only ONE label (either key or reverse)
                    if (roadLabels.containsKey(key)) {
                        roadLabels.get(key).setText(String.valueOf(liveWeight));
                    } else if (roadLabels.containsKey(dst + "-" + src)) {
                        roadLabels.get(dst + "-" + src).setText(String.valueOf(liveWeight));
                    }
                });
        }
    }

    private void removeRoad(int src, int dest) {
        graph.removeRoad(src, dest);
        String key = src + "-" + dest, rev = dest + "-" + src;
        if (roads.containsKey(key)) {
            graphPane.getChildren().removeAll(roads.get(key), roadLabels.get(key));
            roads.remove(key);
            roads.remove(rev);
            roadLabels.remove(key);
            roadLabels.remove(rev);
        }
    }

    private void highlightShortestPath(int s, int e) {
        List<Integer> path = graph.findShortestPath(s, e);
        if (path.isEmpty()) {
            travelTimeLabel.setText("Total Travel Time: 0 (no path found)");
        } else {
            int totalTime = 0;

            roads.values().forEach(r -> {
                r.setStroke(Color.LIGHTGRAY);
                r.setStrokeWidth(2);
            });

            for (int i = 0; i < path.size() - 1; i++) {
                int from = path.get(i);
                int to = path.get(i + 1);

                String key1 = from + "-" + to;
                String key2 = to + "-" + from;
                Line r = roads.getOrDefault(key1, roads.get(key2));
                if (r != null) {
                    r.setStroke(Color.DEEPSKYBLUE);
                    r.setStrokeWidth(4);
                }

                int liveWeight = graph.getLiveWeightBetween(from, to);
                totalTime += liveWeight;

                String labelKey = roadLabels.containsKey(key1) ? key1 : key2;
                if (roadLabels.containsKey(labelKey)) {
                    roadLabels.get(labelKey).setText(String.valueOf(liveWeight));
                }

                System.out.println("Edge from " + from + " to " + to + " has live weight: " + liveWeight);
            }

            travelTimeLabel.setText("Total Travel Time: " + totalTime);
        }
    }

    private void simulateEmergency(int s, int e) {
        List<Integer> path = graph.findShortestPath(s, e);
        if (path.isEmpty()) return;

        // === Backup original weights ===
        Map<String, Integer> originalWeights = new HashMap<>();
        Set<Integer> pathNodes = new HashSet<>(path);

        // Lower weight for emergency path, and store
        for (int i = 0; i < path.size() - 1; i++) {
            int from = path.get(i);
            int to = path.get(i + 1);

            for (Edge edge : graph.getGraph().get(from)) {
                if (edge.destination == to) {
                    String key = from + "-" + to;
                    if (!originalWeights.containsKey(key))
                        originalWeights.put(key, edge.baseWeight);
                    edge.baseWeight = Math.max(5, edge.baseWeight - 10);
                }
            }

            for (Edge edge : graph.getGraph().get(to)) {
                if (edge.destination == from) {
                    String key = to + "-" + from;
                    if (!originalWeights.containsKey(key))
                        originalWeights.put(key, edge.baseWeight);
                    edge.baseWeight = Math.max(5, edge.baseWeight - 10);
                }
            }
        }

        // Increase weight (congestion) on roads adjacent to path nodes
        for (int node : pathNodes) {
            for (Edge edge : graph.getGraph().get(node)) {
                int neighbor = edge.destination;
                if (!pathNodes.contains(neighbor)) {
                    String key = node + "-" + neighbor;
                    if (!originalWeights.containsKey(key)) {
                        originalWeights.put(key, edge.baseWeight);
                        edge.baseWeight += 5; // simulate detoured traffic
                    }
                }
            }
        }

        updateRoadColors();

        // === Animate Ambulance ===
        /**
         * SimulateEmergency:
         * - Finds shortest path from start to end
         * - Temporarily lowers weights along that path
         * - Increases weights for adjacent roads to simulate detours
         * - Animates an ambulance traveling the path
         * - Restores original weights after animation
         */
        Path p = new Path();
        double[] start = {nodes.get(path.get(0)).getCenterX(), nodes.get(path.get(0)).getCenterY()};
        p.getElements().add(new MoveTo(start[0], start[1]));

        for (int i = 1; i < path.size(); i++) {
            Circle n = nodes.get(path.get(i));
            p.getElements().add(new LineTo(n.getCenterX(), n.getCenterY()));

            String k = path.get(i - 1) + "-" + path.get(i);
            if (roads.containsKey(k))
                roads.get(k).setStroke(Color.FIREBRICK);
        }

        Rectangle ambulance = new Rectangle(20, 12, Color.RED);
        ambulance.setArcWidth(6);
        ambulance.setArcHeight(6);
        ambulance.setStroke(Color.WHITE);
        ambulance.setStrokeWidth(1.5);
        ambulance.setEffect(new Glow(0.7));
        graphPane.getChildren().add(ambulance);

        PathTransition anim = new PathTransition(Duration.seconds(5), p, ambulance);
        anim.setOnFinished(ev -> {
            graphPane.getChildren().remove(ambulance);

            // === Restore all original weights ===
            for (Map.Entry<String, Integer> entry : originalWeights.entrySet()) {
                String[] parts = entry.getKey().split("-");
                int from = Integer.parseInt(parts[0]);
                int to = Integer.parseInt(parts[1]);
                int original = entry.getValue();

                for (Edge edge : graph.getGraph().get(from)) {
                    if (edge.destination == to) {
                        edge.baseWeight = original;
                    }
                }
            }

            updateRoadColors();
        });

        anim.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}