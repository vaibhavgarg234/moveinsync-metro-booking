package com.moveinsync.metro.graph;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.dto.MetroDto.OptimizationStrategy;
import com.moveinsync.metro.dto.MetroDto.PathResult;
import com.moveinsync.metro.dto.MetroDto.PathSegment;
import com.moveinsync.metro.model.Route;
import com.moveinsync.metro.model.RouteStop;
import com.moveinsync.metro.model.Stop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * In-memory weighted bidirectional graph of the metro network.
 *
 * <p>Node = Stop, Edge = consecutive stops on a Route with travel time weight.
 * Interchange nodes are stops present on more than one route.
 *
 * <p>Thread-safe via ReadWriteLock – many concurrent route searches, exclusive graph rebuild.
 */
@Component
@Slf4j
public class MetroGraph {

    // ── Internal structures ────────────────────────────────────────────────────

    /** Directed edge in the graph. */
    record Edge(String toStopId, String routeId, double travelTime) {}

    /** Node metadata. */
    record StopMeta(String name, boolean interchange) {}

    /** Route metadata. */
    record RouteMeta(String name, String color) {}

    /**
     * State node for Dijkstra's priority queue.
     * Natural ordering is by cost (used by PriorityQueue).
     */
    record DijkstraState(
            double cost,
            int stops,
            int transfers,
            String stopId,
            String routeId,           // route used to arrive here (null = source)
            List<StepRecord> path     // path taken so far
    ) implements Comparable<DijkstraState> {
        @Override public int compareTo(DijkstraState o) {
            return Double.compare(this.cost, o.cost);
        }
    }

    record StepRecord(String stopId, String routeId) {}

    // ── State ─────────────────────────────────────────────────────────────────

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private Map<String, List<Edge>> adj          = new HashMap<>();
    private Map<String, StopMeta>  stopMeta      = new HashMap<>();
    private Map<String, RouteMeta> routeMeta     = new HashMap<>();
    private boolean                built         = false;

    @Value("${metro.graph.transfer-penalty-minutes:5.0}")
    private double transferPenalty;

    // ── Build / Refresh ────────────────────────────────────────────────────────

    /**
     * (Re-)builds the graph from DB-loaded entities.
     * Called on startup and after any admin mutation.
     */
    public void build(List<Stop> stops, List<Route> routes) {
        rwLock.writeLock().lock();
        try {
            Map<String, List<Edge>> newAdj      = new HashMap<>();
            Map<String, StopMeta>  newStopMeta  = new HashMap<>();
            Map<String, RouteMeta> newRouteMeta = new HashMap<>();

            for (Stop s : stops) {
                newStopMeta.put(s.getId(), new StopMeta(s.getName(), s.isInterchange()));
                newAdj.putIfAbsent(s.getId(), new ArrayList<>());
            }

            for (Route r : routes) {
                newRouteMeta.put(r.getId(), new RouteMeta(r.getName(), r.getColor()));

                List<RouteStop> rStops = r.getRouteStops().stream()
                        .sorted(Comparator.comparingInt(RouteStop::getPosition))
                        .collect(Collectors.toList());

                for (int i = 0; i < rStops.size() - 1; i++) {
                    RouteStop a = rStops.get(i);
                    RouteStop b = rStops.get(i + 1);
                    double t = a.getTravelTimeToNext();

                    // Bidirectional
                    newAdj.computeIfAbsent(a.getStop().getId(), k -> new ArrayList<>())
                          .add(new Edge(b.getStop().getId(), r.getId(), t));
                    newAdj.computeIfAbsent(b.getStop().getId(), k -> new ArrayList<>())
                          .add(new Edge(a.getStop().getId(), r.getId(), t));
                }
            }

            this.adj       = newAdj;
            this.stopMeta  = newStopMeta;
            this.routeMeta = newRouteMeta;
            this.built     = true;

            int edgeCount = newAdj.values().stream().mapToInt(List::size).sum();
            log.info("Metro graph rebuilt: {} stops, {} routes, {} directed edges",
                    newStopMeta.size(), newRouteMeta.size(), edgeCount);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ── Path Finding ──────────────────────────────────────────────────────────

    public PathResult findPath(String source, String destination, OptimizationStrategy strategy) {
        rwLock.readLock().lock();
        try {
            return dijkstra(source, destination, strategy);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private PathResult dijkstra(String source, String destination, OptimizationStrategy strategy) {

        if (!stopMeta.containsKey(source)) {
            return noPath(source, destination, "Source stop '" + source + "' not found in graph");
        }
        if (!stopMeta.containsKey(destination)) {
            return noPath(source, destination, "Destination stop '" + destination + "' not found in graph");
        }
        if (source.equals(destination)) {
            return new PathResult(true, source, destination, 1, 0, 0,
                    List.of(), "Source and destination are the same stop");
        }

        // dist key: stopId + ":" + routeId (null → "null")
        Map<String, Double> dist = new HashMap<>();
        PriorityQueue<DijkstraState> pq = new PriorityQueue<>();

        List<StepRecord> initPath = new ArrayList<>();
        initPath.add(new StepRecord(source, null));

        DijkstraState init = new DijkstraState(0.0, 0, 0, source, null, initPath);
        pq.add(init);
        dist.put(stateKey(source, null), 0.0);

        while (!pq.isEmpty()) {
            DijkstraState cur = pq.poll();

            if (cur.stopId().equals(destination)) {
                return buildResult(source, destination, cur);
            }

            double curKey = dist.getOrDefault(stateKey(cur.stopId(), cur.routeId()), Double.MAX_VALUE);
            if (cur.cost() > curKey) continue;

            for (Edge edge : adj.getOrDefault(cur.stopId(), List.of())) {
                boolean isTransfer = cur.routeId() != null && !edge.routeId().equals(cur.routeId());
                double edgeCost = computeCost(strategy, edge.travelTime(), isTransfer);
                double newCost = cur.cost() + edgeCost;
                int newTransfers = cur.transfers() + (isTransfer ? 1 : 0);

                String key = stateKey(edge.toStopId(), edge.routeId());
                if (newCost < dist.getOrDefault(key, Double.MAX_VALUE)) {
                    dist.put(key, newCost);

                    List<StepRecord> newPath = new ArrayList<>(cur.path());
                    newPath.add(new StepRecord(edge.toStopId(), edge.routeId()));

                    pq.add(new DijkstraState(newCost, cur.stops() + 1, newTransfers,
                            edge.toStopId(), edge.routeId(), newPath));
                }
            }
        }

        return noPath(source, destination, "No path found between the given stops");
    }

    // ── Cost Functions ────────────────────────────────────────────────────────

    private double computeCost(OptimizationStrategy strategy, double travelTime, boolean isTransfer) {
        return switch (strategy) {
            case MIN_STOPS     -> 1.0;
            case MIN_TIME      -> travelTime + (isTransfer ? transferPenalty : 0);
            case MIN_TRANSFERS -> (isTransfer ? 10_000.0 : 0) + travelTime * 0.001;
            case WEIGHTED      -> travelTime + (isTransfer ? transferPenalty : 0);
        };
    }

    // ── Result Builder ────────────────────────────────────────────────────────

    private PathResult buildResult(String source, String destination, DijkstraState state) {
        List<StepRecord> path = state.path();
        List<PathSegment> segments = new ArrayList<>();

        String segRouteId = path.size() > 1 ? path.get(1).routeId() : null;
        List<String> segStopIds   = new ArrayList<>();
        List<String> segStopNames = new ArrayList<>();
        double       segTime      = 0.0;
        int          numTransfers = 0;

        segStopIds.add(path.get(0).stopId());
        segStopNames.add(stopName(path.get(0).stopId()));

        for (int i = 1; i < path.size(); i++) {
            StepRecord cur  = path.get(i);
            StepRecord prev = path.get(i - 1);

            if (!cur.routeId().equals(segRouteId) && segRouteId != null) {
                // Flush segment
                segments.add(makeSegment(segRouteId, segStopIds, segStopNames, segTime, !segments.isEmpty()));
                segStopIds   = new ArrayList<>(List.of(prev.stopId()));
                segStopNames = new ArrayList<>(List.of(stopName(prev.stopId())));
                segRouteId   = cur.routeId();
                segTime      = 0.0;
                numTransfers++;
            }

            double t = travelTimeBetween(prev.stopId(), cur.stopId(), cur.routeId());
            segTime += t;
            segStopIds.add(cur.stopId());
            segStopNames.add(stopName(cur.stopId()));
        }

        if (segRouteId != null && !segStopIds.isEmpty()) {
            segments.add(makeSegment(segRouteId, segStopIds, segStopNames, segTime, !segments.isEmpty()));
        }

        double totalTime  = segments.stream().mapToDouble(PathSegment::travelTime).sum();
        long   totalStops = path.stream().map(StepRecord::stopId).distinct().count();

        return new PathResult(true, source, destination,
                (int) totalStops, totalTime, numTransfers, segments, "Route found");
    }

    private PathSegment makeSegment(String routeId, List<String> stopIds, List<String> stopNames,
                                    double time, boolean isInterchange) {
        RouteMeta rm = routeMeta.getOrDefault(routeId, new RouteMeta(routeId, "unknown"));
        return new PathSegment(routeId, rm.name(), rm.color(),
                List.copyOf(stopIds), List.copyOf(stopNames), time, isInterchange);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String stateKey(String stopId, String routeId) {
        return stopId + ":" + (routeId == null ? "null" : routeId);
    }

    private String stopName(String stopId) {
        StopMeta m = stopMeta.get(stopId);
        return m != null ? m.name() : stopId;
    }

    private double travelTimeBetween(String from, String to, String routeId) {
        return adj.getOrDefault(from, List.of()).stream()
                .filter(e -> e.toStopId().equals(to) && e.routeId().equals(routeId))
                .mapToDouble(Edge::travelTime)
                .findFirst()
                .orElse(2.0);
    }

    private PathResult noPath(String src, String dst, String msg) {
        return new PathResult(false, src, dst, 0, 0, 0, List.of(), msg);
    }

    public boolean isBuilt()           { return built; }
    public boolean stopExists(String id) { return stopMeta.containsKey(id); }
    public int     stopCount()         { return stopMeta.size(); }
}