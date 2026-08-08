package com.wzz.lobotocraft.client.room;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Client-only company cross-section generated from Lobotomy Corporation's
 * original MapGraph_final2 and SefiraIsolateNode data.
 *
 * <p>The source node names remain part of every area id. World rooms can later
 * bind their {@code FacilityAreaEntry.groupId} to one of these ids without
 * depending on screen pixels or changing the mouse-command protocol.</p>
 */
public final class FacilityLayoutTemplate {

    private static final String MAP_RESOURCE =
            "/assets/lobotocraft/facility/map_graph_final2.xml";
    private static final String ISOLATE_RESOURCE =
            "/assets/lobotocraft/facility/sefira_isolate_nodes.xml";
    private static final String PREFAB_BOUNDS_RESOURCE =
            "/assets/lobotocraft/facility/prefab_bounds.json";
    private static final double ORTHOGONAL_EPSILON = 1.0E-9D;
    // isolate_1 sprite rect / PPU transformed by IsolateRoom.RoomSprite scale.
    private static final double CONTAINMENT_LEFT = -3.678621D;
    private static final double CONTAINMENT_RIGHT = 3.809180D;
    private static final double CONTAINMENT_BOTTOM = -2.337975D;
    private static final double CONTAINMENT_TOP = 2.277021D;
    private static final double LAYOUT_PADDING = 2.4D;
    private static final FacilityLayoutTemplate DEFAULT = loadOriginalLayout();

    private final List<Area> areas;
    private final List<Connection> connections;
    private final Map<String, Area> areasById;
    private final Map<String, List<Area>> areasByDepartment;
    private final List<Area> hitOrder;

    private FacilityLayoutTemplate(List<Area> areas, List<Connection> connections) {
        this.areas = List.copyOf(areas);
        this.connections = List.copyOf(connections);

        Map<String, Area> areaIndex = new LinkedHashMap<>();
        Map<String, List<Area>> departmentIndex = new LinkedHashMap<>();
        for (Area area : this.areas) {
            if (areaIndex.put(area.areaId(), area) != null) {
                throw new IllegalArgumentException("Duplicate facility area id: " + area.areaId());
            }
            departmentIndex.computeIfAbsent(area.departmentId(), ignored -> new ArrayList<>()).add(area);
        }
        this.areasById = Collections.unmodifiableMap(areaIndex);

        Map<String, List<Area>> immutableDepartmentIndex = new LinkedHashMap<>();
        departmentIndex.forEach((id, departmentAreas) ->
                immutableDepartmentIndex.put(id, List.copyOf(departmentAreas)));
        this.areasByDepartment = Collections.unmodifiableMap(immutableDepartmentIndex);

        Set<String> connectionIds = new HashSet<>();
        for (Connection connection : this.connections) {
            if (!connectionIds.add(connection.connectionId())) {
                throw new IllegalArgumentException(
                        "Duplicate facility connection id: " + connection.connectionId());
            }
            if (!areaIndex.containsKey(connection.fromAreaId())
                    || !areaIndex.containsKey(connection.toAreaId())) {
                throw new IllegalArgumentException(
                        "Connection references an unknown area: " + connection.connectionId());
            }
        }

        List<Area> orderedForHits = new ArrayList<>(this.areas);
        orderedForHits.sort(Comparator
                .comparingInt((Area area) -> area.role().hitPriority()).reversed()
                .thenComparingDouble(area -> area.bounds().area())
                .thenComparing(Area::areaId));
        this.hitOrder = List.copyOf(orderedForHits);
    }

    public static FacilityLayoutTemplate defaultLayout() {
        return DEFAULT;
    }

    /** Areas are returned in painter order: backgrounds, then operational rooms. */
    public List<Area> allAreas() {
        return areas;
    }

    public List<Connection> allConnections() {
        return connections;
    }

    public Optional<Area> areaById(String areaId) {
        return Optional.ofNullable(areasById.get(areaId));
    }

    public List<Area> areasForDepartment(String departmentId) {
        return areasByDepartment.getOrDefault(departmentId, List.of());
    }

    public Optional<Area> hitTest(double normalizedX, double normalizedY) {
        if (!isNormalized(normalizedX) || !isNormalized(normalizedY)) return Optional.empty();
        for (Area area : hitOrder) {
            if (area.bounds().contains(normalizedX, normalizedY)) return Optional.of(area);
        }
        return Optional.empty();
    }

    public List<Area> hitTestAll(double normalizedX, double normalizedY) {
        if (!isNormalized(normalizedX) || !isNormalized(normalizedY)) return List.of();
        List<Area> hits = new ArrayList<>();
        for (Area area : hitOrder) {
            if (area.bounds().contains(normalizedX, normalizedY)) hits.add(area);
        }
        return List.copyOf(hits);
    }

    public enum AreaRole {
        DEPARTMENT_BACKGROUND(0),
        CORRIDOR(10),
        ELEVATOR_SHAFT(15),
        ELEVATOR_ROOM(20),
        CONTAINMENT_ROOM(30),
        REST_ROOM(30),
        MAIN_HALL(40);

        private final int hitPriority;

        AreaRole(int hitPriority) {
            this.hitPriority = hitPriority;
        }

        public int hitPriority() {
            return hitPriority;
        }
    }

    public record Point(double x, double y) {
        public Point {
            requireNormalized(x, "x");
            requireNormalized(y, "y");
        }
    }

    public record Bounds(double minX, double minY, double maxX, double maxY) {
        public Bounds {
            requireNormalized(minX, "minX");
            requireNormalized(minY, "minY");
            requireNormalized(maxX, "maxX");
            requireNormalized(maxY, "maxY");
            if (maxX <= minX || maxY <= minY) {
                throw new IllegalArgumentException("Facility bounds must have a positive size");
            }
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }

        public double area() {
            return width() * height();
        }

        public Point center() {
            return new Point((minX + maxX) * 0.5D, (minY + maxY) * 0.5D);
        }

        public boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    public record Area(String areaId, String departmentId, FacilityAreaType type,
                       AreaRole role, Bounds bounds) {
        public Area {
            requireId(areaId, "areaId");
            requireId(departmentId, "departmentId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(bounds, "bounds");
        }

        public Point center() {
            return bounds.center();
        }
    }

    /** A polyline whose consecutive segments are guaranteed to be orthogonal. */
    public record Connection(String connectionId, String fromAreaId, String toAreaId,
                             List<Point> points) {
        public Connection {
            requireId(connectionId, "connectionId");
            requireId(fromAreaId, "fromAreaId");
            requireId(toAreaId, "toAreaId");
            points = List.copyOf(points);
            if (points.size() < 2) {
                throw new IllegalArgumentException("A facility connection needs at least two points");
            }
            for (int i = 1; i < points.size(); i++) {
                Point first = points.get(i - 1);
                Point second = points.get(i);
                if (!nearlyEqual(first.x(), second.x())
                        && !nearlyEqual(first.y(), second.y())) {
                    throw new IllegalArgumentException(
                            "Facility connection is not orthogonal: " + connectionId);
                }
            }
        }

        public boolean connects(String areaId) {
            return fromAreaId.equals(areaId) || toAreaId.equals(areaId);
        }
    }

    private record Section(String sectionId, String departmentId) {
    }

    private record SourceNode(String id, double x, double y, String passageAreaId) {
    }

    private record SourcePassage(Element element, Section section, String areaId) {
    }

    private record PrefabGeometry(WorldBounds bounds, String spaceObjectType) {
    }

    private record SourceArea(String areaId, String sectionId, String departmentId,
                              FacilityAreaType type, AreaRole role, WorldBounds bounds) {
    }

    private record SourceConnection(String id, String fromAreaId, String toAreaId,
                                    WorldPoint from, WorldPoint to) {
    }

    private record WorldPoint(double x, double y) {
    }

    private record WorldBounds(double minX, double minY, double maxX, double maxY) {
        private WorldBounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                    || maxX <= minX || maxY <= minY) {
                throw new IllegalArgumentException("Invalid source facility bounds");
            }
        }

        WorldBounds merge(WorldBounds other) {
            return new WorldBounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
        }

        WorldBounds expand(double amount) {
            return new WorldBounds(minX - amount, minY - amount,
                    maxX + amount, maxY + amount);
        }

        WorldBounds translate(double x, double y) {
            return new WorldBounds(minX + x, minY + y, maxX + x, maxY + y);
        }
    }

    private record Normalizer(double minX, double minY, double maxX, double maxY) {
        Bounds bounds(WorldBounds source) {
            return new Bounds(x(source.minX), y(source.maxY), x(source.maxX), y(source.minY));
        }

        Point point(WorldPoint source) {
            return new Point(x(source.x), y(source.y));
        }

        private double x(double sourceX) {
            return (sourceX - minX) / (maxX - minX);
        }

        private double y(double sourceY) {
            return (maxY - sourceY) / (maxY - minY);
        }
    }

    private static FacilityLayoutTemplate loadOriginalLayout() {
        Document map = readXml(MAP_RESOURCE);
        Document isolates = readXml(ISOLATE_RESOURCE);
        Map<String, PrefabGeometry> prefabGeometry = readPrefabGeometry();

        Map<String, SourceNode> nodes = new LinkedHashMap<>();
        List<SourcePassage> sourcePassages = new ArrayList<>();
        List<SourceArea> sourceAreas = new ArrayList<>();

        NodeList areaElements = map.getElementsByTagName("area");
        for (int i = 0; i < areaElements.getLength(); i++) {
            Element originalArea = (Element) areaElements.item(i);
            Section section = sectionForArea(originalArea.getAttribute("name"));
            if (section == null) continue;

            for (Element passage : directChildren(originalArea, "passage")) {
                List<Element> passageNodes = directChildren(passage, "node");
                if (passageNodes.isEmpty()) continue;

                String sourceNodeId = passageNodes.get(0).getAttribute("id");
                String areaId = "dept/" + section.sectionId + "/passage/" + sanitizeId(sourceNodeId);
                for (Element node : passageNodes) {
                    String nodeId = node.getAttribute("id");
                    SourceNode sourceNode = new SourceNode(nodeId,
                            number(node, "x", 0.0D), number(node, "y", 0.0D), areaId);
                    if (nodes.put(nodeId, sourceNode) != null) {
                        throw new IllegalStateException("Duplicate original map node: " + nodeId);
                    }
                }
                sourcePassages.add(new SourcePassage(passage, section, areaId));
            }
        }

        // Bounds are resolved only after every graph node exists. Elevator shafts
        // depend on edge neighbours that often belong to the following passage.
        for (SourcePassage source : sourcePassages) {
            String prefab = source.element.getAttribute("src");
            if (prefab.isBlank()) continue; // group@46 is topological only in the original game.
            PrefabGeometry geometry = prefabGeometry.get(prefab);
            if (geometry == null) {
                throw new IllegalStateException("Missing original prefab bounds for " + prefab);
            }
            String passageType = source.element.getAttribute("passageType")
                    .toLowerCase(Locale.ROOT);
            AreaRole role = passageRole(passageType, geometry.spaceObjectType);
            WorldBounds bounds = geometry.bounds.translate(
                    number(source.element, "x", 0.0D),
                    number(source.element, "y", 0.0D));
            sourceAreas.add(new SourceArea(source.areaId, source.section.sectionId,
                    source.section.departmentId, facilityType(role), role, bounds));
        }

        Map<String, SourceArea> passageByNode = new HashMap<>();
        Map<String, SourceArea> sourceById = new LinkedHashMap<>();
        for (SourceArea area : sourceAreas) sourceById.put(area.areaId, area);
        for (SourceNode node : nodes.values()) {
            SourceArea area = sourceById.get(node.passageAreaId);
            if (area != null) passageByNode.put(node.id, area);
        }

        List<SourceConnection> sourceConnections = readPassageConnections(
                map, nodes, passageByNode);
        readContainments(isolates, nodes, passageByNode, sourceAreas, sourceConnections);

        WorldBounds extent = null;
        Map<String, WorldBounds> sectionBounds = new LinkedHashMap<>();
        Map<String, String> sectionDepartments = new LinkedHashMap<>();
        for (SourceArea area : sourceAreas) {
            extent = extent == null ? area.bounds : extent.merge(area.bounds);
            sectionBounds.merge(area.sectionId, area.bounds, WorldBounds::merge);
            sectionDepartments.putIfAbsent(area.sectionId, area.departmentId);
        }
        if (extent == null) throw new IllegalStateException("Original facility layout is empty");
        extent = extent.expand(LAYOUT_PADDING);
        double sourceWidth = extent.maxX - extent.minX;
        double sourceHeight = extent.maxY - extent.minY;
        double sourceSpan = Math.max(sourceWidth, sourceHeight);
        double sourceCenterX = (extent.minX + extent.maxX) * 0.5D;
        double sourceCenterY = (extent.minY + extent.maxY) * 0.5D;
        Normalizer normalizer = new Normalizer(
                sourceCenterX - sourceSpan * 0.5D, sourceCenterY - sourceSpan * 0.5D,
                sourceCenterX + sourceSpan * 0.5D, sourceCenterY + sourceSpan * 0.5D);

        List<Area> areas = new ArrayList<>();
        for (Map.Entry<String, WorldBounds> entry : sectionBounds.entrySet()) {
            String sectionId = entry.getKey();
            areas.add(new Area("dept/" + sectionId + "/background",
                    sectionDepartments.get(sectionId), FacilityAreaType.OTHER,
                    AreaRole.DEPARTMENT_BACKGROUND,
                    normalizer.bounds(entry.getValue().expand(0.8D))));
        }
        for (SourceArea source : sourceAreas) {
            areas.add(new Area(source.areaId, source.departmentId, source.type,
                    source.role, normalizer.bounds(source.bounds)));
        }

        List<Connection> connections = new ArrayList<>();
        for (SourceConnection source : sourceConnections) {
            Point start = normalizer.point(source.from);
            Point end = normalizer.point(source.to);
            connections.add(new Connection(source.id, source.fromAreaId, source.toAreaId,
                    orthogonalPoints(start, end)));
        }
        return new FacilityLayoutTemplate(areas, connections);
    }

    private static List<SourceConnection> readPassageConnections(
            Document map, Map<String, SourceNode> nodes,
            Map<String, SourceArea> passageByNode) {
        List<SourceConnection> result = new ArrayList<>();
        Set<String> connectedAreaPairs = new HashSet<>();
        NodeList edges = map.getElementsByTagName("edge");
        for (int i = 0; i < edges.getLength(); i++) {
            Element edge = (Element) edges.item(i);
            String firstId = edge.getAttribute("node1");
            String secondId = edge.getAttribute("node2");
            SourceNode first = nodes.get(firstId);
            SourceNode second = nodes.get(secondId);
            SourceArea firstArea = passageByNode.get(firstId);
            SourceArea secondArea = passageByNode.get(secondId);
            if (first == null || second == null || firstArea == null || secondArea == null
                    || firstArea.areaId.equals(secondArea.areaId)) continue;

            String pair = firstArea.areaId.compareTo(secondArea.areaId) < 0
                    ? firstArea.areaId + "|" + secondArea.areaId
                    : secondArea.areaId + "|" + firstArea.areaId;
            if (!connectedAreaPairs.add(pair)) continue;
            result.add(new SourceConnection(
                    "edge/" + sanitizeId(firstId) + "/" + sanitizeId(secondId),
                    firstArea.areaId, secondArea.areaId,
                    new WorldPoint(first.x, first.y), new WorldPoint(second.x, second.y)));
        }
        return result;
    }

    private static void readContainments(Document isolates,
                                         Map<String, SourceNode> nodes,
                                         Map<String, SourceArea> passageByNode,
                                         List<SourceArea> areas,
                                         List<SourceConnection> connections) {
        NodeList sefiras = isolates.getElementsByTagName("sefira");
        for (int i = 0; i < sefiras.getLength(); i++) {
            Element sefira = (Element) sefiras.item(i);
            Section section = sectionForSefira(sefira.getAttribute("name"));
            if (section == null) continue;

            for (Element node : directChildren(sefira, "node")) {
                int slotIndex = (int) number(node, "index", 0.0D);
                if (!"architecture".equals(section.departmentId) && slotIndex >= 4) {
                    continue;
                }
                String anchorId = node.getAttribute("id");
                SourceNode anchor = nodes.get(anchorId);
                SourceArea passage = passageByNode.get(anchorId);
                if (anchor == null || passage == null) continue;

                double x = number(node, "x", anchor.x);
                double y = number(node, "y", anchor.y);
                String containmentId = "dept/" + section.sectionId
                        + "/containment/" + sanitizeId(anchorId);
                areas.add(new SourceArea(containmentId, section.sectionId,
                        section.departmentId, FacilityAreaType.CONTAINMENT,
                        AreaRole.CONTAINMENT_ROOM,
                        containmentBounds(x, y)));
                connections.add(new SourceConnection(
                        "containment-edge/" + sanitizeId(anchorId), passage.areaId,
                        containmentId, new WorldPoint(anchor.x, anchor.y), new WorldPoint(x, y)));
            }
        }
    }

    private static AreaRole passageRole(String passageType, String spaceObjectType) {
        return switch (passageType) {
            case "sefira" -> AreaRole.MAIN_HALL;
            case "dept" -> AreaRole.REST_ROOM;
            case "vertical" -> AreaRole.ELEVATOR_SHAFT;
            case "horizontal" -> "HUBSHORT".equals(spaceObjectType)
                    || "HUBLONG".equals(spaceObjectType)
                    ? AreaRole.ELEVATOR_ROOM : AreaRole.CORRIDOR;
            default -> AreaRole.CORRIDOR;
        };
    }

    private static FacilityAreaType facilityType(AreaRole role) {
        return switch (role) {
            case CORRIDOR -> FacilityAreaType.CORRIDOR;
            case ELEVATOR_ROOM -> FacilityAreaType.ELEVATOR;
            case ELEVATOR_SHAFT -> FacilityAreaType.ELEVATOR_SHAFT;
            case CONTAINMENT_ROOM -> FacilityAreaType.CONTAINMENT;
            case REST_ROOM, MAIN_HALL -> FacilityAreaType.REST_ROOM;
            case DEPARTMENT_BACKGROUND -> FacilityAreaType.OTHER;
        };
    }

    private static Section sectionForArea(String originalArea) {
        return switch (originalArea) {
            case "1" -> new Section("control", "control");
            case "2" -> new Section("safety", "safety");
            case "3" -> new Section("training", "training");
            case "4" -> new Section("information", "information");
            case "5" -> new Section("central/upper", "central");
            case "6" -> new Section("central/lower", "central");
            case "7" -> new Section("disciplinary", "disciplinary");
            case "8" -> new Section("welfare", "welfare");
            case "9" -> new Section("extraction", "extraction");
            case "10" -> new Section("record", "record");
            case "11" -> new Section("architecture", "architecture");
            // Area 12 is Daat, the hidden story area rather than a normal department.
            default -> null;
        };
    }

    private static Section sectionForSefira(String sefira) {
        return switch (sefira) {
            case "Malkut" -> sectionForArea("1");
            case "Netzach" -> sectionForArea("2");
            case "Hod" -> sectionForArea("3");
            case "Yesod" -> sectionForArea("4");
            case "Tiphereth1" -> sectionForArea("5");
            case "Tiphereth2" -> sectionForArea("6");
            case "Geburah" -> sectionForArea("7");
            case "Chesed" -> sectionForArea("8");
            case "Binah" -> sectionForArea("9");
            case "Chokhmah" -> sectionForArea("10");
            case "Kether" -> sectionForArea("11");
            default -> null;
        };
    }

    private static Document readXml(String resourcePath) {
        try (InputStream stream = FacilityLayoutTemplate.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IllegalStateException("Missing facility resource " + resourcePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(stream);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load original facility geometry", exception);
        }
    }

    private static Map<String, PrefabGeometry> readPrefabGeometry() {
        try (InputStream stream = FacilityLayoutTemplate.class
                .getResourceAsStream(PREFAB_BOUNDS_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing facility resource " + PREFAB_BOUNDS_RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject prefabs = root.getAsJsonObject("prefabs");
            Map<String, PrefabGeometry> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : prefabs.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                WorldBounds bounds = new WorldBounds(
                        value.get("minX").getAsDouble(), value.get("minY").getAsDouble(),
                        value.get("maxX").getAsDouble(), value.get("maxY").getAsDouble());
                result.put(entry.getKey(), new PrefabGeometry(bounds,
                        value.get("spaceObjectType").getAsString()));
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load original passage prefab bounds", exception);
        }
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals(tagName)) {
                result.add(element);
            }
        }
        return result;
    }

    private static double number(Element element, String attribute, double fallback) {
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) return fallback;
        return Double.parseDouble(value);
    }

    private static WorldBounds containmentBounds(double x, double y) {
        return new WorldBounds(x + CONTAINMENT_LEFT, y + CONTAINMENT_BOTTOM,
                x + CONTAINMENT_RIGHT, y + CONTAINMENT_TOP);
    }

    private static List<Point> orthogonalPoints(Point start, Point end) {
        if (nearlyEqual(start.x, end.x) || nearlyEqual(start.y, end.y)) {
            return List.of(start, end);
        }
        return List.of(start, new Point(start.x, end.y), end);
    }

    private static String sanitizeId(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean previousDash = false;
        for (int i = 0; i < value.length(); i++) {
            char character = Character.toLowerCase(value.charAt(i));
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9' || character == '_';
            if (valid) {
                result.append(character);
                previousDash = false;
            } else if (!previousDash) {
                result.append('-');
                previousDash = true;
            }
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '-') {
            result.setLength(result.length() - 1);
        }
        return result.isEmpty() ? "unnamed" : result.toString();
    }

    private static void requireNormalized(double value, String name) {
        if (!isNormalized(value)) {
            throw new IllegalArgumentException(name + " must be within [0, 1]");
        }
    }

    private static boolean isNormalized(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '/' || character == '-' || character == '_';
            if (!valid) {
                throw new IllegalArgumentException(name + " contains an invalid character: " + value);
            }
        }
    }

    private static boolean nearlyEqual(double first, double second) {
        return Math.abs(first - second) <= ORTHOGONAL_EPSILON;
    }
}
