package com.moveinsync.metro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

// ── Stop DTOs ──────────────────────────────────────────────────────────────────

public class MetroDto {

    public record StopRequest(
            @NotBlank String id,
            @NotBlank String name,
            Double latitude,
            Double longitude
    ) {}

    public record StopResponse(
            String id,
            String name,
            boolean interchange,
            Double latitude,
            Double longitude,
            LocalDateTime createdAt
    ) {}

    // ── Route DTOs ─────────────────────────────────────────────────────────────

    public record RouteStopEntry(
            @NotBlank String stopId,
            @Min(0) double travelTimeToNext
    ) {}

    public record RouteRequest(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String color,
            @NotNull @Size(min = 2) List<@Valid RouteStopEntry> stops
    ) {}

    public record RouteStopResponse(String stopId, int position, double travelTimeToNext) {}

    public record RouteResponse(
            String id,
            String name,
            String color,
            List<RouteStopResponse> stops,
            LocalDateTime createdAt
    ) {}

    // ── Bulk imports ───────────────────────────────────────────────────────────

    public record BulkStopImport(@NotEmpty List<@Valid StopRequest> stops) {}

    public record BulkRouteImport(@NotEmpty List<@Valid RouteRequest> routes) {}

    public record ImportResult(String message, int newCount) {}

    // ── Path / Graph DTOs ──────────────────────────────────────────────────────

    public enum OptimizationStrategy {
        MIN_STOPS, MIN_TIME, MIN_TRANSFERS, WEIGHTED
    }

    public record PathSegment(
            String routeId,
            String routeName,
            String routeColor,
            List<String> stopIds,
            List<String> stopNames,
            double travelTime,
            boolean isInterchangeStart
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PathResult(
            boolean found,
            String source,
            String destination,
            int totalStops,
            double totalTime,
            int numTransfers,
            List<PathSegment> segments,
            String message
    ) {}

    // ── Booking DTOs ───────────────────────────────────────────────────────────

    public record BookingRequest(
            @NotBlank String sourceStopId,
            @NotBlank String destinationStopId,
            String userId,
            OptimizationStrategy strategy
    ) {
        public BookingRequest {
            if (strategy == null) strategy = OptimizationStrategy.WEIGHTED;
        }
    }

    public record BookingResponse(
            String id,
            String userId,
            String sourceStopId,
            String destinationStopId,
            Object routePath,
            int totalStops,
            double totalTime,
            int numTransfers,
            String qrString,
            String status,
            LocalDateTime createdAt
    ) {}

    // ── QR Validation ──────────────────────────────────────────────────────────

    public record QrValidateRequest(@NotBlank String qrString) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QrValidateResponse(boolean valid, String bookingId, String status, String message) {}

    // ── Health ─────────────────────────────────────────────────────────────────

    public record HealthResponse(String status, boolean graphBuilt, int totalStops) {}
}