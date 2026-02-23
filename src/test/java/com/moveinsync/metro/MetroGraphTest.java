package com.moveinsync.metro;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.graph.MetroGraph;
import com.moveinsync.metro.model.Route;
import com.moveinsync.metro.model.RouteStop;
import com.moveinsync.metro.model.Stop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetroGraph Dijkstra engine.
 *
 * Network used in tests:
 *
 *  RED LINE:  A --3m-- B --4m-- C(interchange) --2m-- D
 *  BLUE LINE: E --5m-- C(interchange) --3m-- F
 *
 *  Interchange at C. Journey A→F requires RED then BLUE.
 */
class MetroGraphTest {

    private MetroGraph graph;

    @BeforeEach
    void setUp() {
        graph = new MetroGraph();

        Stop a = stop("A", "Alpha");
        Stop b = stop("B", "Beta");
        Stop c = stop("C", "Gamma (Interchange)");
        Stop d = stop("D", "Delta");
        Stop e = stop("E", "Epsilon");
        Stop f = stop("F", "Zeta");

        Route red  = route("RED",  "Red Line",  "#FF0000", List.of(
                rs(a, 0, 3), rs(b, 1, 4), rs(c, 2, 2), rs(d, 3, 0)
        ));
        Route blue = route("BLUE", "Blue Line", "#0000FF", List.of(
                rs(e, 0, 5), rs(c, 1, 3), rs(f, 2, 0)
        ));

        graph.build(List.of(a, b, c, d, e, f), List.of(red, blue));
    }

    @Test
    void directRoute_sameLineNoTransfer() {
        MetroDto.PathResult result = graph.findPath("A", "D", MetroDto.OptimizationStrategy.WEIGHTED);
        assertThat(result.found()).isTrue();
        assertThat(result.numTransfers()).isZero();
        assertThat(result.totalTime()).isEqualTo(9.0);  // 3+4+2
        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).routeId()).isEqualTo("RED");
    }

    @Test
    void interchangeRoute_twoLines() {
        MetroDto.PathResult result = graph.findPath("A", "F", MetroDto.OptimizationStrategy.WEIGHTED);
        assertThat(result.found()).isTrue();
        assertThat(result.numTransfers()).isEqualTo(1);
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().get(0).routeId()).isEqualTo("RED");
        assertThat(result.segments().get(1).routeId()).isEqualTo("BLUE");
        assertThat(result.segments().get(1).isInterchangeStart()).isTrue();
    }

    @Test
    void sameSourceDestination_returnsSuccess_zeroTime() {
        MetroDto.PathResult result = graph.findPath("A", "A", MetroDto.OptimizationStrategy.WEIGHTED);
        assertThat(result.found()).isTrue();
        assertThat(result.totalTime()).isZero();
        assertThat(result.numTransfers()).isZero();
    }

    @Test
    void noPath_disconnectedStop() {
        MetroDto.PathResult result = graph.findPath("A", "E", MetroDto.OptimizationStrategy.WEIGHTED);
        assertThat(result.found()).isFalse();
        assertThat(result.message()).containsIgnoringCase("no path");
    }

    @Test
    void unknownStop_returnsNotFound() {
        MetroDto.PathResult result = graph.findPath("UNKNOWN", "A", MetroDto.OptimizationStrategy.WEIGHTED);
        assertThat(result.found()).isFalse();
        assertThat(result.message()).containsIgnoringCase("not found");
    }

    @Test
    void minStops_strategy() {
        MetroDto.PathResult result = graph.findPath("A", "C", MetroDto.OptimizationStrategy.MIN_STOPS);
        assertThat(result.found()).isTrue();
    }

    @Test
    void minTransfers_strategy_prefersDirect() {
        // A → D: no transfer on RED (cost 0)
        MetroDto.PathResult result = graph.findPath("A", "D", MetroDto.OptimizationStrategy.MIN_TRANSFERS);
        assertThat(result.numTransfers()).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Stop stop(String id, String name) {
        Stop s = new Stop();
        s.setId(id);
        s.setName(name);
        s.setRouteStops(new ArrayList<>());
        return s;
    }

    private RouteStop rs(Stop stop, int pos, double time) {
        RouteStop rs = new RouteStop();
        rs.setStop(stop);
        rs.setPosition(pos);
        rs.setTravelTimeToNext(time);
        return rs;
    }

    private Route route(String id, String name, String color, List<RouteStop> rsList) {
        Route r = new Route();
        r.setId(id);
        r.setName(name);
        r.setColor(color);
        r.setRouteStops(new ArrayList<>(rsList));
        for (RouteStop rs : rsList) rs.setRoute(r);
        return r;
    }
}