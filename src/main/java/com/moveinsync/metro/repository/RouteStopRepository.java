package com.moveinsync.metro.repository;

import com.moveinsync.metro.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByRouteIdOrderByPosition(String routeId);

    void deleteByRouteId(String routeId);

    /**
     * Returns stop IDs that appear in more than one route → interchange candidates.
     */
    @Query("SELECT rs.stop.id FROM RouteStop rs GROUP BY rs.stop.id HAVING COUNT(DISTINCT rs.route.id) > 1")
    List<String> findInterchangeStopIds();
}