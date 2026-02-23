package com.moveinsync.metro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Stop {

    @Id
    @Column(nullable = false, unique = true)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_interchange")
    private boolean interchange = false;

    private Double latitude;
    private Double longitude;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "stop", fetch = FetchType.LAZY)
    private List<RouteStop> routeStops = new ArrayList<>();
}