package com.moveinsync.metro.config;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.service.MetroService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AppStartupRunner implements ApplicationRunner {

    private final MetroService metroService;

    @Value("${metro.seed-demo-data:false}")
    private boolean seedDemoData;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Loading metro graph from database...");
        metroService.refreshGraph();

        if (seedDemoData) {
            seedDemo();
        }
    }

    private void seedDemo() {
        log.info("Seeding demo metro data...");
        try {
            metroService.bulkImportStops(List.of(
                    new MetroDto.StopRequest("S1", "Rajiv Chowk",        28.6331, 77.2197),
                    new MetroDto.StopRequest("S2", "Kashmere Gate",       28.6677, 77.2275),
                    new MetroDto.StopRequest("S3", "Chandni Chowk",       28.6567, 77.2302),
                    new MetroDto.StopRequest("S4", "New Delhi",           28.6429, 77.2193),
                    new MetroDto.StopRequest("S5", "Central Secretariat", 28.6144, 77.2117),
                    new MetroDto.StopRequest("S6", "Barakhamba Road",     28.6327, 77.2268),
                    new MetroDto.StopRequest("S7", "Mandi House",         28.6268, 77.2378),
                    new MetroDto.StopRequest("S8", "ITO",                 28.6286, 77.2415)
            ));

            metroService.bulkImportRoutes(List.of(
                    new MetroDto.RouteRequest("YL", "Yellow Line", "#FFD700", List.of(
                            new MetroDto.RouteStopEntry("S2", 3),
                            new MetroDto.RouteStopEntry("S3", 2),
                            new MetroDto.RouteStopEntry("S1", 2),
                            new MetroDto.RouteStopEntry("S5", 3)
                    )),
                    new MetroDto.RouteRequest("BL", "Blue Line", "#0000CD", List.of(
                            new MetroDto.RouteStopEntry("S6", 2),
                            new MetroDto.RouteStopEntry("S1", 2),
                            new MetroDto.RouteStopEntry("S7", 3),
                            new MetroDto.RouteStopEntry("S8", 4)
                    )),
                    new MetroDto.RouteRequest("VL", "Violet Line", "#8B00FF", List.of(
                            new MetroDto.RouteStopEntry("S4", 2),
                            new MetroDto.RouteStopEntry("S2", 3),
                            new MetroDto.RouteStopEntry("S8", 5)
                    ))
            ));
            log.info("Demo data seeded successfully");
        } catch (Exception e) {
            log.warn("Demo seed skipped (likely already seeded): {}", e.getMessage());
        }
    }
}