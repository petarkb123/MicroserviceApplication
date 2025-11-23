# Analytics Microservice

A fitness analytics microservice that tracks your workouts, calculates stats, and helps you stay motivated with personal records and milestones. Think of it as your personal trainer that never sleeps and keeps all your fitness data organized.

## What's This About?

This is a REST API microservice built with Spring Boot that handles all the analytics stuff for a fitness tracking app. It crunches numbers, tracks your progress, and celebrates your achievements (well, it creates milestone records, which is pretty close).

## Tech Stack

We're using:
- **Java 17** - Modern Java features
- **Spring Boot 3.4.5** - Makes building APIs a breeze
- **Maven** - Dependency management
- **MySQL 8.0** - Stores all your workout data
- **Spring Data JPA** - Database interactions made easy

The app runs on port `1010` by default, and everything communicates via REST API with JSON.

## Getting Started

### What You'll Need

Before you start, make sure you have:
- Java 17 or higher installed
- Maven 3.8+ (or just use the included `mvnw` wrapper - it's easier)
- MySQL 8.0 running on your machine

### Quick Setup

1. **Clone this repo**
```bash
   git clone https://github.com/petarkb123/MicroserviceApplication.git
   cd MicroserviceApplication
   ```

2. **Make sure MySQL is running**
   
   The app expects MySQL to be running on `localhost:3306`. Don't worry about creating the database - it'll create itself when you first run the app.

3. **Set up your database credentials**
   
   Create a file called `application-local.properties` in `src/main/resources/` with your MySQL username and password:
```properties
spring.datasource.username=root
   spring.datasource.password=your_mysql_password
   ```
   
   ⚠️ **Important:** This file won't be committed to git, so your password stays safe.

4. **Open it in IntelliJ IDEA**
   
   Just open the project folder in IntelliJ. It should detect it's a Maven project automatically and start downloading dependencies. Give it a minute to finish.

5. **Run it!**
   
   Right-click on `AnalyticsApplication.java` and hit "Run", or use the command line:
```bash
./mvnw spring-boot:run
```

   If everything worked, you should see the app start on port `1010`.

### Alternative: Environment Variables

If you don't want to create a properties file, you can set environment variables instead:
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
```

## What Can This Thing Do?

### Analytics Features

- **Weekly Statistics** - See your workout breakdown day by day, total sessions, volume, and more
- **Training Frequency** - Know how often you're hitting the gym, your current streak, and your consistency
- **Exercise Volume Trends** - Track how your volume changes over time for each exercise
- **Progressive Overload Tracking** - See your progress as you gradually increase weights and volume
- **Personal Records** - Automatically detects and tracks your PRs (max weight, max volume, max reps)
- **Milestones** - Create custom milestones or let the system generate them for you

### Automatic Milestones

The app automatically creates milestones when you:
- Complete your first workout ("Getting Started")
- Hit 50 workouts ("Dedicated")
- Hit 100 workouts ("Centurion")
- Join the 100K, 500K, or 1M pound clubs (total volume)
- Work out 12 times in 30 days ("Consistency")
- Build a training streak ("Consistency King")

Pretty neat, right?

## API Endpoints

All endpoints are under `/api/analytics` and require a `X-User-Id` header with a valid UUID.

### Main Endpoints

- `GET /weekly` - Get weekly workout statistics (requires `from` and `to` date parameters)
- `GET /sessions` - Get summaries of all workout sessions in a date range
- `GET /training-frequency` - Analyze your training frequency and streaks
- `GET /volume-trends` - See volume trends for each exercise over time
- `GET /progressive-overload` - Track progressive overload patterns
- `GET /personal-records` - Get all your personal records and milestones
- `GET /milestones` - List all your milestones
- `POST /milestones` - Create a new custom milestone
- `PUT /milestones/{id}` - Update an existing milestone
- `DELETE /milestones/{id}` - Delete a milestone
- `POST /weekly/recompute` - Force recalculation of weekly statistics

### Example Requests

Get your weekly stats:
```bash
curl -X GET "http://localhost:1010/api/analytics/weekly?from=2024-01-01&to=2024-01-07" \
  -H "X-User-Id: 123e4567-e89b-12d3-a456-426614174000"
```

Create a milestone:
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

## How It Works

This microservice gets data from a main fitness app via sync endpoints. When you do a workout in the main app, it sends the data here, and this service handles all the analytics and milestone stuff.

The internal sync endpoints (under `/api/analytics/internal`) are used by the main application to:
- Sync exercise data when exercises are created or updated
- Sync workout sessions when you finish a workout
- Delete exercises or workouts when they're removed from the main app

## Database

The database is called `fitness_analytics` and it's created automatically when you first run the app. All entities use UUIDs as primary keys, and the schema updates automatically thanks to Hibernate's DDL mode.

## Testing

Run the tests with:
```bash
mvn test
```

To see test coverage:
```bash
mvn test jacoco:report
```

Then check out `target/site/jacoco/index.html` in your browser.

We've got pretty good test coverage (90%+) with unit tests, integration tests, and API tests.

## Error Handling

If something goes wrong, you'll get a JSON error response that looks like:
```json
{
  "error": "Error Type",
  "message": "What went wrong",
  "timestamp": "2024-01-01T12:00:00"
}
```

The app validates all requests and returns appropriate HTTP status codes (400 for bad requests, 403 for unauthorized stuff, 404 for not found, etc.).

## Performance & Scalability

- Weekly summaries are cached in the database for faster access
- All queries are optimized with proper indexing on userId and date ranges
- Stateless design means you can scale horizontally if needed
- Transaction management keeps everything consistent

## Security

User identification happens via the `X-User-Id` header. All queries are filtered by userId, so users can only see their own data. No cross-user data access is possible.

## Troubleshooting

**The app won't start:**
- Make sure MySQL is running on localhost:3306
- Check that you created `application-local.properties` with correct credentials
- Verify Java 17 is installed: `java -version`

**Database connection errors:**
- Double-check your MySQL username and password in `application-local.properties`
- Make sure MySQL is actually running
- Check if port 3306 is available

**Port already in use:**
- Change the port in `application.properties`: `server.port=1011` (or any other free port)

## What's Next?

Some ideas for future improvements:
- More caching for frequently accessed data
- Batch processing for large datasets
- Real-time analytics updates
- Machine learning predictions (because why not?)
- Export your analytics data

## License

This project is for educational purposes. Feel free to learn from it, but please don't use it in production without proper security reviews.

## Questions?

If you run into issues or have questions, check the main application documentation or reach out to the development team.

Happy coding! 💪
