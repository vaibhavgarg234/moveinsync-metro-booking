package com.moveinsync.metro.controller;

import com.moveinsync.metro.dto.MetroDto;
import com.moveinsync.metro.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create and manage metro bookings")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a metro booking. Returns 422 if no path exists — booking is NOT created.")
    public MetroDto.BookingResponse createBooking(@Valid @RequestBody MetroDto.BookingRequest req) {
        return bookingService.createBooking(req);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Fetch booking details including route segments and QR string")
    public MetroDto.BookingResponse getBooking(@PathVariable String bookingId) {
        return bookingService.getBooking(bookingId);
    }

    @DeleteMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel an ACTIVE booking")
    public MetroDto.BookingResponse cancelBooking(@PathVariable String bookingId) {
        return bookingService.cancelBooking(bookingId);
    }

    @PostMapping("/validate-qr")
    @Operation(summary = "Validate a QR string – checks HMAC integrity and booking status")
    public MetroDto.QrValidateResponse validateQr(@Valid @RequestBody MetroDto.QrValidateRequest req) {
        return bookingService.validateQr(req.qrString());
    }
}