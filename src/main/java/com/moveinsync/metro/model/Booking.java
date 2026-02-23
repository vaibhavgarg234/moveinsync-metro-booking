package com.moveinsync.metro.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {

    @Id
    @Column(nullable = false, unique = true)
    private String id;

    private String userId;

    @Column(nullable = false)
    private String sourceStopId;

    @Column(nullable = false)
    private String destinationStopId;

    /** Serialised JSON path details stored as CLOB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private String routePathJson;

    private int totalStops;
    private double totalTime;
    private int numTransfers;

    @Column(nullable = false, unique = true)
    private String qrString;

    @Builder.Default
    private String status = "ACTIVE";   // ACTIVE | USED | CANCELLED

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}