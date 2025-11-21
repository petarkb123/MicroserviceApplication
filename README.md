# Analytics Microservice

A dedicated REST API microservice for advanced fitness analytics, providing comprehensive workout statistics, personal records tracking, and milestone management.

## Technology Stack

### Backend
- **Java**: Version 17
- **Spring Boot**: Version 3.4.5
- **Build Tool**: Maven
- **Database**: MySQL 8.0
- **Framework Modules**:
    - Spring Web (REST API)
    - Spring Data JPA (Database access)
    - Spring Boot Actuator (Monitoring)
    - Spring Validation (Request validation)

### Architecture
- **Port**: 1010 (configurable via `application.properties`)
- **Communication**: REST API with JSON request/response
- **Database**: Separate `fitness_analytics_db` database
- **Integration**: Called via Feign Client from Main Application

## Project Structure

### Domain Entities
- **Milestone**: User achievement tracking (custom and system-generated)
- **Exercise**: Exercise metadata (synced from main app)
- **WorkoutSession**: Workout session data (synced from main app)
- **WorkoutSet**: Individual set data (synced from main app)
- **WeeklySummarySnapshot**: Cached weekly statistics

### Services
- **AnalyticsService**: Core analytics calculations and business logic
- **SyncService**: Data synchronization from main application

### Controllers
- **AnalyticsController**: Public REST API endpoints for analytics
- **SyncController**: Internal endpoints for data synchronization

## API Endpoints

### Base URL
All endpoints are prefixed with `/api/analytics`

### Authentication
All endpoints require the `X-User-Id` header with a valid UUID.

### Public Analytics Endpoints

#### Weekly Statistics
- **GET** `/weekly`
    - Parameters: `from` (LocalDate), `to` (LocalDate)
    - Returns: `WeeklySummaryResponse` with daily breakdown
    - Description: Returns weekly workout statistics including session count, volume, and daily breakdown

#### Session Summaries
- **GET** `/sessions`
    - Parameters: `from` (LocalDate), `to` (LocalDate)
    - Returns: `List<SessionSummaryResponse>`
    - Description: Returns summaries of all workout sessions in the date range

#### Training Frequency
- **GET** `/training-frequency`
    - Parameters: `from` (LocalDate), `to` (LocalDate)
    - Returns: `TrainingFrequencyResponse`
    - Description: Analyzes training frequency including average sessions per week, current streak, and weekly breakdown

#### Exercise Volume Trends
- **GET** `/volume-trends`
    - Parameters: `from` (LocalDate), `to` (LocalDate)
    - Returns: `List<ExerciseVolumeTrendDto>`
    - Description: Shows volume trends for each exercise over time

#### Progressive Overload
- **GET** `/progressive-overload`
    - Parameters: `from` (LocalDate), `to` (LocalDate)
    - Returns: `List<ProgressiveOverloadDto>`
    - Description: Tracks progressive overload patterns for exercises

#### Personal Records
- **GET** `/personal-records`
    - Returns: `PersonalRecordsDto`
    - Description: Returns all personal records (PRs) and milestones for the user

#### Milestones Management
- **GET** `/milestones`
    - Returns: `List<MilestoneDto>`
    - Description: Returns all user milestones

- **POST** `/milestones`
    - Body: `CreateMilestoneRequest`
    - Returns: `MilestoneDto`
    - Description: Creates a new custom milestone

- **PUT** `/milestones/{id}`
    - Body: `UpdateMilestoneRequest`
    - Returns: `MilestoneDto`
    - Description: Updates an existing milestone

- **DELETE** `/milestones/{id}`
    - Returns: `204 No Content`
    - Description: Deletes a milestone

#### Weekly Statistics Recompute
- **POST** `/weekly/recompute`
    - Body: `RecomputeWeeklyRequest` (optional from/to dates)
    - Returns: `WeeklySummaryResponse`
    - Description: Forces recalculation of weekly statistics

### Internal Sync Endpoints

Base URL: `/api/analytics/internal`

These endpoints are used internally by the main application for data synchronization.

- **POST** `/exercises` - Sync exercise data
- **DELETE** `/exercises/{exerciseId}` - Delete exercise
- **POST** `/workouts` - Sync workout session data
- **DELETE** `/workouts/{workoutId}` - Delete workout session

## Features

### Analytics Capabilities

1. **Weekly Statistics**
    - Daily workout breakdown
    - Total sessions, sets, reps, and volume
    - Date range filtering

2. **Training Frequency Analysis**
    - Average sessions per week
    - Current training streak
    - Weekly breakdown with consistency metrics
    - Best and worst weeks identification

3. **Exercise Volume Trends**
    - Per-exercise volume tracking over time
    - Weekly volume aggregation
    - Exercise identification and grouping

4. **Progressive Overload Tracking**
    - Exercise-specific progress tracking
    - Weight and volume progression
    - Progress point identification

5. **Personal Records (PRs)**
    - Automatic PR detection (max weight, max volume, max reps)
    - Exercise-specific PR tracking
    - Milestone integration

6. **Milestone Management**
    - Custom milestone creation
    - System-generated milestones
    - Milestone types: PERSONAL_RECORD, CUSTOM, etc.
    - Achievement date tracking

### Data Synchronization

The microservice receives data from the main application via sync endpoints:

- **Exercise Sync**: Exercise metadata (name, muscle group, equipment)
- **Workout Sync**: Complete workout sessions with sets
- **Deletion Sync**: Cascade deletion of related data

### Automatic Milestone Generation

The service automatically generates milestones for:
- First workout (Getting Started)
- 50 workouts (Dedicated)
- 100 workouts (Centurion)
- 100K, 500K, 1M pound clubs (volume milestones)
- Consistency achievements (12 workouts in 30 days)
- Training streaks (Consistency King)

## Database

### Configuration
- **Database Name**: `fitness_analytics_db`
- **Technology**: MySQL 8.0
- **UUIDs**: All entities use UUID primary keys
- **DDL Mode**: `update` (auto-creates/updates schema)

### Entity Relationships
- `WorkoutSession` → `WorkoutSet` (OneToMany)
- `Milestone` → `User` (implicit via userId)
- `WeeklySummarySnapshot` → `User` (implicit via userId)

## Data Validation & Error Handling

### Validation
- **Request Headers**: `X-User-Id` must be present and valid UUID
- **Date Parameters**: Must be valid ISO dates, `from` must be before `to`
- **Request Bodies**: Validated using `@Valid` and Jakarta Validation

### Error Handling
- **GlobalExceptionHandler**: Centralized exception handling
- **Error Responses**: JSON format with appropriate HTTP status codes
- **Validation Errors**: 400 Bad Request with descriptive messages
- **Authorization Errors**: 403 Forbidden for unauthorized operations
- **Not Found Errors**: 404 Not Found for missing resources

### Error Response Format
```json
{
  "error": "Error Type",
  "message": "Detailed error message",
  "timestamp": "2024-01-01T12:00:00"
}
```

## Testing

### Test Coverage
- **Line Coverage**: 90%+
- **Test Count**: 40+ tests
- **Test Types**: Unit tests, Integration tests, API tests

### Test Classes
- **Service Tests**:
    - `AnalyticsServiceTest`: Comprehensive analytics logic testing
    - `SyncServiceTest`: Data synchronization testing
- **Controller Tests**:
    - `AnalyticsControllerIntegrationTest`: REST API endpoint testing
    - `SyncControllerTest`: Internal sync endpoint testing
- **Exception Handler Tests**:
    - `GlobalExceptionHandlerTest`: Error handling validation

### Running Tests
```bash
mvn test
```

### Test Coverage Report
```bash
mvn test jacoco:report
```
View report at: `target/site/jacoco/index.html`

## Setup & Running

### Prerequisites
- **Java 17** (JDK 17 or higher)
- **Maven 3.8+** (or use the included `mvnw` wrapper)
- **MySQL 8.0** (installed and running on localhost:3306)

### Quick Start Guide

1. **Clone the repository**
   ```bash
   git clone https://github.com/petarkb123/MicroserviceApplication.git
   cd MicroserviceApplication
   ```

2. **Ensure MySQL is running**
   - MySQL server should be running on `localhost:3306`
   - The database will be created automatically if it doesn't exist

3. **Configure database credentials**
   
   Create `src/main/resources/application-local.properties`:
   ```properties
   spring.datasource.username=root
   spring.datasource.password=your_mysql_password
   ```
   
   **Important:** This file is ignored by git and will not be committed to the repository.

4. **Open in IntelliJ IDEA**
   - Open the project as a Maven project
   - IntelliJ will automatically detect the project structure
   - Wait for Maven dependencies to download

5. **Run the application**
   - Right-click on `AnalyticsApplication.java` → Run
   - Or use the command line: `./mvnw spring-boot:run`
   - The application will start on port `1010`

### Database Setup

The database will be created automatically on first run. If you prefer to create it manually:
```sql
CREATE DATABASE IF NOT EXISTS fitness_analytics_db;
```

### Configuration

#### Option 1: Create Local Properties File (Recommended)
Create a file `src/main/resources/application-local.properties` with your database credentials:
```properties
spring.datasource.username=root
spring.datasource.password=your_mysql_password
```

This file is ignored by git (your credentials stay private) and will be automatically loaded by Spring Boot.

#### Option 2: Use Environment Variables
Set the following environment variables before running the application:
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
```

#### Option 3: Direct Configuration (Not Recommended)
You can also edit `src/main/resources/application.properties` directly and replace the placeholder values.

**Note:** The database will be created automatically on first run if it doesn't exist (see `createDatabaseIfNotExist=true` in the connection URL).

### Running the Application
```bash
./mvnw spring-boot:run
```

Or using Maven:
```bash
mvn spring-boot:run
```

### Port
Default port: `1010` (configurable in `application.properties`)

## Integration with Main Application

The microservice is called from the main application using Spring Cloud OpenFeign:

### Feign Client Configuration
- **Client Interface**: `AnalyticsClient` (in main app)
- **Base URL**: `http://localhost:1010/api/analytics`
- **Header**: `X-User-Id` automatically added
- **Error Handling**: Graceful fallback on service unavailability

### Sync Flow
1. Main app creates/updates exercise → Calls sync endpoint
2. Main app finishes workout → Calls sync endpoint with session and sets
3. Main app deletes exercise/workout → Calls delete endpoint
4. Microservice updates its database accordingly

## Logging

### Logging Configuration
- **Framework**: SLF4J with Logback
- **Levels**: INFO, WARN, DEBUG, ERROR
- **Package Logging**: `project.fitnessanalytics=INFO`

### Logged Events
- API endpoint access
- Data synchronization operations
- Error conditions
- Transaction boundaries
- Analytics calculations

## Code Quality

### Architecture Principles
- **RESTful Design**: Standard HTTP methods and status codes
- **Layered Architecture**: Controller → Service → Repository
- **Separation of Concerns**: Analytics logic separated from sync logic
- **Transaction Management**: Read-only transactions for analytics, write transactions for sync

### Code Standards
- **Naming Conventions**: Follow Java standards
- **No Dead Code**: All classes and methods in use
- **Clean Imports**: No unused imports
- **Proper Encapsulation**: Private fields with getters/setters
- **Single Responsibility**: One purpose per class/method

## API Examples

### Get Weekly Statistics
```bash
curl -X GET "http://localhost:1010/api/analytics/weekly?from=2024-01-01&to=2024-01-07" \
  -H "X-User-Id: 123e4567-e89b-12d3-a456-426614174000"
```

### Create Milestone
```bash
curl -X POST "http://localhost:1010/api/analytics/milestones" \
  -H "X-User-Id: 123e4567-e89b-12d3-a456-426614174000" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "title": "First 100kg Bench",
    "description": "Achieved 100kg bench press",
    "achievedDate": "2024-01-15",
    "type": "PERSONAL_RECORD"
  }'
```

### Get Personal Records
```bash
curl -X GET "http://localhost:1010/api/analytics/personal-records" \
  -H "X-User-Id: 123e4567-e89b-12d3-a456-426614174000"
```

## Performance Considerations

### Caching
- Weekly summary snapshots cached in database
- Recompute endpoint for cache refresh

### Database Optimization
- Indexed queries on userId and date ranges
- Efficient aggregation queries
- Transaction management for consistency

### Scalability
- Stateless design (no session state)
- Horizontal scaling support
- Database connection pooling

## Security

### Authentication
- User identification via `X-User-Id` header
- User context validation on all operations
- Authorization checks for milestone operations

### Data Isolation
- All queries filtered by userId
- No cross-user data access
- Secure deletion operations

## Monitoring

### Actuator Endpoints
- Health checks available
- Metrics collection
- Application monitoring

## Future Enhancements

Potential improvements:
- Caching layer for frequently accessed analytics
- Batch processing for large data sets
- Real-time analytics updates
- Advanced machine learning predictions
- Export functionality for analytics data

## License

This project is developed for educational purposes as part of a fitness tracking application.

## Contact

For questions or support, please refer to the main application documentation or contact the development team.

