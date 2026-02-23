package com.moveinsync.metro.exception;

public class MetroException {

    public static class StopNotFoundException extends RuntimeException {
        public StopNotFoundException(String stopId) {
            super("Stop not found: " + stopId);
        }
    }

    public static class RouteNotFoundException extends RuntimeException {
        public RouteNotFoundException(String routeId) {
            super("Route not found: " + routeId);
        }
    }

    public static class BookingNotFoundException extends RuntimeException {
        public BookingNotFoundException(String bookingId) {
            super("Booking not found: " + bookingId);
        }
    }

    public static class DuplicateResourceException extends RuntimeException {
        public DuplicateResourceException(String message) {
            super(message);
        }
    }

    public static class NoPathException extends RuntimeException {
        public NoPathException(String message) {
            super(message);
        }
    }

    public static class GraphNotReadyException extends RuntimeException {
        public GraphNotReadyException() {
            super("Metro graph is not initialised. Load stops and routes first.");
        }
    }

    public static class InvalidBookingStateException extends RuntimeException {
        public InvalidBookingStateException(String message) {
            super(message);
        }
    }
}