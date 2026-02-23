package com.moveinsync.metro.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_stops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(nullable = false)
    private int position;

    /** Travel time in minutes to the NEXT stop in this route (0 for last stop). */
    @Column(name = "travel_time_to_next")
    private double travelTimeToNext = 2.0;
}