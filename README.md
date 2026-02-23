# MoveInSync Metro Booking Service

**Java 17 · Spring Boot 3.2 · H2/PostgreSQL · Dijkstra Graph Engine**

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Application                      │
│                                                                        │
│  ┌──────────────────────┐       ┌──────────────────────────────────┐  │
│  │   MetroController    │       │      BookingController            │  │
│  │  /api/metro/stops    │       │  POST /api/bookings               │  │
│  │  /api/metro/routes   │       │  GET  /api/bookings/{id}          │  │
│  │  /api/metro/path     │       │  DELETE /{id}/cancel              │  │
│  │  /api/metro/graph/*  │       │  POST /api/bookings/validate-qr   │  │
│  └──────────┬───────────┘       └──────────────┬─────────────────── ┘  │
│             │                                   │                      │
│             ▼                                   ▼                      │
│  ┌─────────────────────┐      ┌──────────────────────────────────┐   │
│  │    MetroService     │      │         BookingService            │   │
│  └──────────┬──────────┘      └──────────────┬───────────────────┘   │
│             │                                  │                      │
│      ┌──────┴──────────────┬───────────────────┤                      │
│      ▼                     ▼                   ▼                      │
│  ┌─────────┐       ┌─────────────┐      ┌──────────────┐            │
│  │   JPA   │       │  MetroGraph │      │   QrService  │            │
│  │ (H2/PG) │       │  (Dijkstra) │      │  HMAC-SHA256 │            │
│  │ Repos   │       │  In-Memory  │      └──────────────┘            │
│  └─────────┘       └─────────────┘                                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
src/main/java/com/moveinsync/metro/
├── MetroBookingApplication.java         ← Spring Boot entry point
├── config/
│   ├── AppConfig.java                   ← Jackson + OpenAPI beans
│   └── AppStartupRunner.java            ← Graph init + optional demo seed
├── controller/
│   ├── MetroController.java             ← /api/metro/* endpoints
│   ├── BookingController.java           ← /api/bookings/* endpoints
│   └── HealthController.java            ← /health, /
├── dto/
│   └── MetroDto.java                    ← All request/response records
├── exception/
│   ├── MetroException.java              ← Custom exception hierarchy
│   └── GlobalExceptionHandler.java      ← @RestControllerAdvice
├── graph/
│   └── MetroGraph.java                  ← Core Dijkstra engine (thread-safe)
├── model/
│   ├── Stop.java                        ← JPA entity
│   ├── Route.java                       ← JPA entity
│   ├── RouteStop.java                   ← Join entity (route ↔ stop + metadata)
│   └── Booking.java                     ← JPA entity
├── repository/
│   ├── StopRepository.java
│   ├── RouteRepository.java
│   ├── RouteStopRepository.java
│   └── BookingRepository.java
├── service/
│   ├── MetroService.java                ← Stop/Route CRUD + graph refresh
│   └── BookingService.java              ← Booking lifecycle + QR
└── util/
    └── QrService.java                   ← HMAC-SHA256 QR generation/validation

src/test/java/com/moveinsync/metro/
└── MetroGraphTest.java                  ← Unit tests for graph engine
```

---

## Build & Run

### Prerequisites
- Java 17+
- Maven 3.8+

```bash
# Build
mvn clean package -DskipTests

# Run (H2 in-file DB, no demo data)
java -jar target/metro-booking-1.0.0.jar

# Run with demo data seeded
java -jar target/metro-booking-1.0.0.jar --metro.seed-demo-data=true

# Run tests
mvn test
```

### With PostgreSQL (production)

In `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/metro_booking
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

Add the driver to `pom.xml`:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## API Reference

Swagger UI is available at **http://localhost:8080/docs** after startup.

### Metro Management

| Method | Endpoint                        | Description                         |
|--------|---------------------------------|-------------------------------------|
| POST   | `/api/metro/stops`              | Create a stop                       |
| GET    | `/api/metro/stops`              | List all stops                      |
| GET    | `/api/metro/stops/{id}`         | Get stop by ID                      |
| POST   | `/api/metro/stops/bulk`         | Bulk import stops (JSON body)       |
| POST   | `/api/metro/stops/import/csv`   | Import stops from CSV file          |
| POST   | `/api/metro/routes`             | Create a route                      |
| GET    | `/api/metro/routes`             | List all routes                     |
| GET    | `/api/metro/routes/{id}`        | Get route by ID                     |
| POST   | `/api/metro/routes/bulk`        | Bulk import routes (JSON body)      |
| POST   | `/api/metro/routes/import/json` | Import routes from JSON file        |
| GET    | `/api/metro/path`               | Preview path (no booking created)   |
| POST   | `/api/metro/graph/refresh`      | Force graph rebuild from DB         |

### Bookings

| Method | Endpoint                        | Description                          |
|--------|---------------------------------|--------------------------------------|
| POST   | `/api/bookings`                 | Create booking (422 if no path)      |
| GET    | `/api/bookings/{id}`            | Get booking + QR string + segments   |
| DELETE | `/api/bookings/{id}/cancel`     | Cancel an ACTIVE booking             |
| POST   | `/api/bookings/validate-qr`     | Validate HMAC-signed QR token        |

---

## Usage Example

### 1. Create stops

```http
POST /api/metro/stops/bulk
{
  "stops": [
    {"id": "A", "name": "Alpha Station"},
    {"id": "B", "name": "Beta Station"},
    {"id": "C", "name": "Gamma (Interchange)"},
    {"id": "D", "name": "Delta Station"}
  ]
}
```

### 2. Create routes

```http
POST /api/metro/routes/bulk
{
  "routes": [
    {
      "id": "RED", "name": "Red Line", "color": "#FF0000",
      "stops": [
        {"stopId": "A", "travelTimeToNext": 3},
        {"stopId": "B", "travelTimeToNext": 4},
        {"stopId": "C", "travelTimeToNext": 0}
      ]
    },
    {
      "id": "BLUE", "name": "Blue Line", "color": "#0000FF",
      "stops": [
        {"stopId": "C", "travelTimeToNext": 5},
        {"stopId": "D", "travelTimeToNext": 0}
      ]
    }
  ]
}
```

*Stop C is auto-detected as an interchange.*

### 3. Create a booking

```http
POST /api/bookings
{
  "sourceStopId": "A",
  "destinationStopId": "D",
  "userId": "user-123",
  "strategy": "WEIGHTED"
}
```

**Response:**
```json
{
  "id": "a3f8...",
  "sourceStopId": "A",
  "destinationStopId": "D",
  "totalStops": 4,
  "totalTime": 17.0,
  "numTransfers": 1,
  "qrString": "QTNmOC4u...",
  "status": "ACTIVE",
  "routePath": {
    "segments": [
      {
        "routeId": "RED",
        "routeName": "Red Line",
        "stopIds": ["A","B","C"],
        "stopNames": ["Alpha Station","Beta Station","Gamma (Interchange)"],
        "travelTime": 7.0,
        "isInterchangeStart": false
      },
      {
        "routeId": "BLUE",
        "routeName": "Blue Line",
        "stopIds": ["C","D"],
        "stopNames": ["Gamma (Interchange)","Delta Station"],
        "travelTime": 5.0,
        "isInterchangeStart": true
      }
    ]
  }
}
```

---

## Key Design Decisions

### Graph Engine (`MetroGraph.java`)
- **In-memory** weighted bidirectional adjacency list
- **ReadWriteLock** for thread safety — many concurrent route queries, exclusive rebuild
- **Dijkstra** with pluggable cost function per strategy:

| Strategy       | Cost per edge                               |
|----------------|---------------------------------------------|
| `MIN_STOPS`    | 1 per hop                                   |
| `MIN_TIME`     | travel_time + penalty if line change        |
| `MIN_TRANSFERS`| 10,000 if line change + tiny time weight    |
| `WEIGHTED`     | travel_time + 5 min × isTransfer (default)  |

### QR Token (`QrService.java`)
```
base64url( bookingId | source | dest | unixTimestamp | HMAC-SHA256 )
```
- Constant-time HMAC comparison (timing-attack safe)
- Secret key configurable via `metro.qr.secret-key` property

### Interchange Detection
A stop is automatically marked `interchange = true` when it appears in more than one route. Recalculated on every route import via a single JPQL `GROUP BY` query.

### Idempotency
Same `userId` + `sourceStopId` + `destinationStopId` with status `ACTIVE` → returns existing booking instead of creating a duplicate.

---

## Edge Cases

| Scenario                    | Behaviour                                  |
|-----------------------------|--------------------------------------------|
| Same source & destination   | Returns 0-time path, no booking            |
| Stop doesn't exist          | 404 Not Found                              |
| No connecting path          | 422 Unprocessable Entity, no booking       |
| Already booked (same user)  | 201 with existing ACTIVE booking           |
| Circular routes             | Dijkstra handles via cost relaxation       |
| Multiple optimal paths      | Deterministic by PriorityQueue ordering    |