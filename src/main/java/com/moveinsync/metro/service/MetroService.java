package com.moveinsync.metro.service;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.exception.MetroException;
import com.moveinsync.metro.graph.MetroGraph;
import com.moveinsync.metro.model.*;
import com.moveinsync.metro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.opencsv.CSVReader;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetroService {

    private final StopRepository     stopRepo;
    private final RouteRepository    routeRepo;
    private final RouteStopRepository routeStopRepo;
    private final MetroGraph          graph;

    // ── Graph sync ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void refreshGraph() {
        List<Stop>  stops  = stopRepo.findAll();
        List<Route> routes = routeRepo.findAll();
        // eagerly load route stops via route.getRouteStops() (already EAGER)
        graph.build(stops, routes);
        log.info("Graph refreshed via MetroService");
    }

    private void recalculateInterchanges() {
        List<String> interchangeIds = routeStopRepo.findInterchangeStopIds();
        List<Stop> allStops = stopRepo.findAll();
        for (Stop s : allStops) {
            boolean shouldBe = interchangeIds.contains(s.getId());
            if (s.isInterchange() != shouldBe) {
                s.setInterchange(shouldBe);
                stopRepo.save(s);
            }
        }
    }

    // ── Stops ─────────────────────────────────────────────────────────────────

    @Transactional
    public MetroDto.StopResponse createStop(MetroDto.StopRequest req) {
        if (stopRepo.existsById(req.id())) {
            throw new MetroException.DuplicateResourceException("Stop already exists: " + req.id());
        }
        Stop stop = Stop.builder()
                .id(req.id()).name(req.name())
                .latitude(req.latitude()).longitude(req.longitude())
                .build();
        stopRepo.save(stop);
        refreshGraph();
        return toStopResponse(stop);
    }

    @Transactional
    public int bulkImportStops(List<MetroDto.StopRequest> requests) {
        int newCount = 0;
        for (MetroDto.StopRequest req : requests) {
            boolean exists = stopRepo.existsById(req.id());
            if (!exists) newCount++;
            Stop stop = exists ? stopRepo.findById(req.id()).get() : new Stop();
            stop.setId(req.id());
            stop.setName(req.name());
            stop.setLatitude(req.latitude());
            stop.setLongitude(req.longitude());
            stopRepo.save(stop);
        }
        refreshGraph();
        return newCount;
    }

    @Transactional
    public int importStopsFromCsv(String csvContent) throws Exception {
        List<MetroDto.StopRequest> stops = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new StringReader(csvContent))) {
            String[] header = reader.readNext(); // skip header
            String[] row;
            while ((row = reader.readNext()) != null) {
                stops.add(new MetroDto.StopRequest(
                        row[0].trim(), row[1].trim(),
                        row.length > 2 && !row[2].isBlank() ? Double.parseDouble(row[2]) : null,
                        row.length > 3 && !row[3].isBlank() ? Double.parseDouble(row[3]) : null
                ));
            }
        }
        return bulkImportStops(stops);
    }

    public MetroDto.StopResponse getStop(String id) {
        return toStopResponse(stopRepo.findById(id)
                .orElseThrow(() -> new MetroException.StopNotFoundException(id)));
    }

    public List<MetroDto.StopResponse> listStops() {
        return stopRepo.findAll().stream().map(this::toStopResponse).collect(Collectors.toList());
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    @Transactional
    public MetroDto.RouteResponse createRoute(MetroDto.RouteRequest req) {
        if (routeRepo.existsById(req.id())) {
            throw new MetroException.DuplicateResourceException("Route already exists: " + req.id());
        }
        Route route = Route.builder().id(req.id()).name(req.name()).color(req.color()).build();
        routeRepo.save(route);
        attachStops(route, req.stops());
        recalculateInterchanges();
        refreshGraph();
        return toRouteResponse(route);
    }

    @Transactional
    public int bulkImportRoutes(List<MetroDto.RouteRequest> requests) {
        int count = 0;
        for (MetroDto.RouteRequest req : requests) {
            boolean exists = routeRepo.existsById(req.id());
            Route route;
            if (exists) {
                route = routeRepo.findById(req.id()).get();
                routeStopRepo.deleteByRouteId(req.id());
                routeStopRepo.flush();
            } else {
                route = new Route();
                route.setId(req.id());
                count++;
            }
            route.setName(req.name());
            route.setColor(req.color());
            routeRepo.save(route);
            attachStops(route, req.stops());
        }
        recalculateInterchanges();
        refreshGraph();
        return count;
    }

    @Transactional
    public int importRoutesFromJson(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        List<MetroDto.RouteRequest> routes = om.readValue(json,
                om.getTypeFactory().constructCollectionType(List.class, MetroDto.RouteRequest.class));
        return bulkImportRoutes(routes);
    }

    public MetroDto.RouteResponse getRoute(String id) {
        return toRouteResponse(routeRepo.findById(id)
                .orElseThrow(() -> new MetroException.RouteNotFoundException(id)));
    }

    public List<MetroDto.RouteResponse> listRoutes() {
        return routeRepo.findAll().stream().map(this::toRouteResponse).collect(Collectors.toList());
    }

    // ── Path preview ──────────────────────────────────────────────────────────

    public MetroDto.PathResult computePath(String source, String destination,
                                            MetroDto.OptimizationStrategy strategy) {
        if (!graph.isBuilt()) throw new MetroException.GraphNotReadyException();
        return graph.findPath(source, destination, strategy);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void attachStops(Route route, List<MetroDto.RouteStopEntry> entries) {
        for (int pos = 0; pos < entries.size(); pos++) {
            MetroDto.RouteStopEntry entry = entries.get(pos);
            Stop stop = stopRepo.findById(entry.stopId())
                    .orElseThrow(() -> new MetroException.StopNotFoundException(entry.stopId()));
            RouteStop rs = RouteStop.builder()
                    .route(route)
                    .stop(stop)
                    .position(pos)
                    .travelTimeToNext(entry.travelTimeToNext())
                    .build();
            routeStopRepo.save(rs);
        }
    }

    private MetroDto.StopResponse toStopResponse(Stop s) {
        return new MetroDto.StopResponse(s.getId(), s.getName(), s.isInterchange(),
                s.getLatitude(), s.getLongitude(), s.getCreatedAt());
    }

    private MetroDto.RouteResponse toRouteResponse(Route r) {
        List<MetroDto.RouteStopResponse> stops = r.getRouteStops().stream()
                .sorted(java.util.Comparator.comparingInt(RouteStop::getPosition))
                .map(rs -> new MetroDto.RouteStopResponse(
                        rs.getStop().getId(), rs.getPosition(), rs.getTravelTimeToNext()))
                .collect(Collectors.toList());
        return new MetroDto.RouteResponse(r.getId(), r.getName(), r.getColor(), stops, r.getCreatedAt());
    }
}