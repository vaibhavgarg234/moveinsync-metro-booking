package com.moveinsync.metro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.exception.MetroException;
import com.moveinsync.metro.graph.MetroGraph;
import com.moveinsync.metro.model.Booking;
import com.moveinsync.metro.repository.BookingRepository;
import com.moveinsync.metro.util.QrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepo;
    private final MetroGraph        graph;
    private final QrService         qrService;
    private final ObjectMapper      objectMapper;

    // ── Create Booking ────────────────────────────────────────────────────────

    @Transactional
    public MetroDto.BookingResponse createBooking(MetroDto.BookingRequest req) {
        if (!graph.isBuilt()) throw new MetroException.GraphNotReadyException();

        // Validate stop existence
        if (!graph.stopExists(req.sourceStopId())) {
            throw new MetroException.StopNotFoundException(req.sourceStopId());
        }
        if (!graph.stopExists(req.destinationStopId())) {
            throw new MetroException.StopNotFoundException(req.destinationStopId());
        }

        // Idempotency: return existing ACTIVE booking for same user + journey
        if (req.userId() != null) {
            var existing = bookingRepo.findByUserIdAndSourceStopIdAndDestinationStopIdAndStatus(
                    req.userId(), req.sourceStopId(), req.destinationStopId(), "ACTIVE");
            if (existing.isPresent()) {
                log.info("Returning existing booking {} (idempotent)", existing.get().getId());
                return toResponse(existing.get());
            }
        }

        // Compute path
        MetroDto.PathResult path = graph.findPath(req.sourceStopId(), req.destinationStopId(), req.strategy());
        if (!path.found()) {
            throw new MetroException.NoPathException(path.message());
        }

        // Persist booking
        String bookingId = UUID.randomUUID().toString();
        String qrString  = qrService.generateQrString(bookingId, req.sourceStopId(), req.destinationStopId());

        Map<String, Object> routePathData = Map.of(
                "strategy", req.strategy().name(),
                "source", req.sourceStopId(),
                "destination", req.destinationStopId(),
                "segments", path.segments()
        );

        String routePathJson;
        try {
            routePathJson = objectMapper.writeValueAsString(routePathData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize route path", e);
        }

        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(req.userId())
                .sourceStopId(req.sourceStopId())
                .destinationStopId(req.destinationStopId())
                .routePathJson(routePathJson)
                .totalStops(path.totalStops())
                .totalTime(path.totalTime())
                .numTransfers(path.numTransfers())
                .qrString(qrString)
                .status("ACTIVE")
                .build();

        bookingRepo.save(booking);
        log.info("Booking created: {}", bookingId);
        return toResponse(booking);
    }

    // ── Get Booking ───────────────────────────────────────────────────────────

    public MetroDto.BookingResponse getBooking(String bookingId) {
        return toResponse(bookingRepo.findById(bookingId)
                .orElseThrow(() -> new MetroException.BookingNotFoundException(bookingId)));
    }

    // ── Cancel Booking ────────────────────────────────────────────────────────

    @Transactional
    public MetroDto.BookingResponse cancelBooking(String bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new MetroException.BookingNotFoundException(bookingId));

        if (!"ACTIVE".equals(booking.getStatus())) {
            throw new MetroException.InvalidBookingStateException(
                    "Booking '" + bookingId + "' is already " + booking.getStatus());
        }
        booking.setStatus("CANCELLED");
        bookingRepo.save(booking);
        return toResponse(booking);
    }

    // ── QR Validation ─────────────────────────────────────────────────────────

    public MetroDto.QrValidateResponse validateQr(String qrString) {
        QrService.ValidationResult result = qrService.validateQrString(qrString);

        if (!result.valid()) {
            return new MetroDto.QrValidateResponse(false, null, null, result.reason());
        }

        return bookingRepo.findById(result.bookingId())
                .map(b -> {
                    if (!b.getQrString().equals(qrString)) {
                        return new MetroDto.QrValidateResponse(false, b.getId(), null,
                                "QR string does not match booking record");
                    }
                    return new MetroDto.QrValidateResponse(true, b.getId(), b.getStatus(),
                            "Booking is " + b.getStatus());
                })
                .orElse(new MetroDto.QrValidateResponse(false, result.bookingId(), null,
                        "Booking not found in system"));
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private MetroDto.BookingResponse toResponse(Booking b) {
        Object routePath;
        try {
            routePath = objectMapper.readValue(b.getRoutePathJson(), Object.class);
        } catch (Exception e) {
            routePath = b.getRoutePathJson();
        }
        return new MetroDto.BookingResponse(
                b.getId(), b.getUserId(), b.getSourceStopId(), b.getDestinationStopId(),
                routePath, b.getTotalStops(), b.getTotalTime(), b.getNumTransfers(),
                b.getQrString(), b.getStatus(), b.getCreatedAt()
        );
    }
}