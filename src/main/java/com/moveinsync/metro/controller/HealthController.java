package com.moveinsync.metro.controller;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.graph.MetroGraph;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "System")
public class HealthController {

    private final MetroGraph graph;

    @GetMapping("/health")
    public MetroDto.HealthResponse health() {
        return new MetroDto.HealthResponse("ok", graph.isBuilt(), graph.stopCount());
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "MoveInSync Metro Booking Service",
                "version", "1.0.0",
                "docs", "/docs"
        );
    }
}