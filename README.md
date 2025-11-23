# Analytics Microservice

A Spring Boot microservice for tracking fitness workouts and calculating analytics. It handles stats, personal records, and milestones.

## Tech Stack

- Java 17
- Spring Boot 3.4.5
- MySQL 8.0
- Maven

Runs on port `1010` by default.

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/petarkb123/MicroserviceApplication.git
   cd MicroserviceApplication
   ```

2. **Make sure MySQL is running** on `localhost:3306`

3. **Create `application-local.properties`** in `src/main/resources/`:
   ```properties
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```
   This file is ignored by git, so your password stays safe.

4. **Run the app**
   - In IntelliJ: Right-click `AnalyticsApplication.java` → Run
   - Or from terminal: `./mvnw spring-boot:run`

The database `fitness_analytics` will be created automatically.

## Features

- Weekly workout statistics
- Training frequency and streaks
- Exercise volume trends
- Progressive overload tracking
- Personal records (max weight, reps, volume)
- Automatic and custom milestones

## API Endpoints

All endpoints are under `/api/analytics` and require `X-User-Id` header.

- `GET /weekly?from=YYYY-MM-DD&to=YYYY-MM-DD` - Weekly stats
- `GET /sessions?from=YYYY-MM-DD&to=YYYY-MM-DD` - Session summaries
- `GET /training-frequency?from=YYYY-MM-DD&to=YYYY-MM-DD` - Training frequency
- `GET /volume-trends?from=YYYY-MM-DD&to=YYYY-MM-DD` - Volume trends
- `GET /progressive-overload?from=YYYY-MM-DD&to=YYYY-MM-DD` - Progressive overload
- `GET /personal-records` - Personal records and milestones
- `GET /milestones` - List milestones
- `POST /milestones` - Create milestone
- `PUT /milestones/{id}` - Update milestone
- `DELETE /milestones/{id}` - Delete milestone

## Example

```bash
curl -X GET "http://localhost:1010/api/analytics/weekly?from=2024-01-01&to=2024-01-07" \
  -H "X-User-Id: your-user-id-here"
```

## Testing

```bash
mvn test
```

## How It Works

The main fitness app sends workout data to this service via sync endpoints under `/api/analytics/internal`. This service then calculates all the analytics and tracks milestones.

## Troubleshooting

**App won't start:**
- Check MySQL is running
- Verify `application-local.properties` has correct credentials
- Make sure Java 17 is installed

**Port 1010 already in use:**
- Change `server.port` in `application.properties`

## Notes

- Database uses UUIDs for primary keys
- Schema updates automatically via Hibernate
- All queries filter by userId for security
