package com.moveinsync.metro.controller;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.service.MetroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/metro")
@RequiredArgsConstructor
@Tag(name = "Metro Management", description = "Admin APIs for managing stops, routes and the graph")
public class MetroController {

    private final MetroService metroService;

    // ── Stops ─────────────────────────────────────────────────────────────────

    @PostMapping("/stops")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a single metro stop")
    public MetroDto.StopResponse createStop(@Valid @RequestBody MetroDto.StopRequest req) {
        return metroService.createStop(req);
    }

    @GetMapping("/stops")
    @Operation(summary = "List all metro stops")
    public List<MetroDto.StopResponse> listStops() {
        return metroService.listStops();
    }

    @GetMapping("/stops/{stopId}")
    @Operation(summary = "Get a stop by ID")
    public MetroDto.StopResponse getStop(@PathVariable String stopId) {
        return metroService.getStop(stopId);
    }

    @PostMapping("/stops/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bulk import stops via JSON body")
    public MetroDto.ImportResult bulkImportStops(@Valid @RequestBody MetroDto.BulkStopImport req) {
        int count = metroService.bulkImportStops(req.stops());
        return new MetroDto.ImportResult("Stops imported/updated", count);
    }

    @PostMapping(value = "/stops/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import stops from a CSV file (columns: id,name,latitude,longitude)")
    public MetroDto.ImportResult importStopsCsv(@RequestParam("file") MultipartFile file) throws Exception {
        String content = new String(file.getBytes());
        int count = metroService.importStopsFromCsv(content);
        return new MetroDto.ImportResult("CSV imported", count);
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a metro route")
    public MetroDto.RouteResponse createRoute(@Valid @RequestBody MetroDto.RouteRequest req) {
        return metroService.createRoute(req);
    }

    @GetMapping("/routes")
    @Operation(summary = "List all metro routes")
    public List<MetroDto.RouteResponse> listRoutes() {
        return metroService.listRoutes();
    }

    @GetMapping("/routes/{routeId}")
    @Operation(summary = "Get a route by ID")
    public MetroDto.RouteResponse getRoute(@PathVariable String routeId) {
        return metroService.getRoute(routeId);
    }

    @PostMapping("/routes/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bulk import routes via JSON body")
    public MetroDto.ImportResult bulkImportRoutes(@Valid @RequestBody MetroDto.BulkRouteImport req) {
        int count = metroService.bulkImportRoutes(req.routes());
        return new MetroDto.ImportResult("Routes imported/updated", count);
    }

    @PostMapping(value = "/routes/import/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import routes from a JSON file (array of RouteRequest objects)")
    public MetroDto.ImportResult importRoutesJson(@RequestParam("file") MultipartFile file) throws Exception {
        String content = new String(file.getBytes());
        int count = metroService.importRoutesFromJson(content);
        return new MetroDto.ImportResult("JSON imported", count);
    }

    // ── Graph ─────────────────────────────────────────────────────────────────

    @PostMapping("/graph/refresh")
    @Operation(summary = "Force rebuild of the in-memory route graph from DB")
    public ResponseEntity<String> refreshGraph() {
        metroService.refreshGraph();
        return ResponseEntity.ok("Graph refreshed successfully");
    }

    @GetMapping("/path")
    @Operation(summary = "Compute the optimal path between two stops (no booking created)")
    public MetroDto.PathResult computePath(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "WEIGHTED") MetroDto.OptimizationStrategy strategy) {
        return metroService.computePath(source, destination, strategy);
    }
}