# Forex Rate Service

## Constraints and Assumptions
- The service should support at least 10,000 successful requests per day with 1 API token
- The rate should not be older than 5 minutes
- The One-Frame service supports a maximum of 1,000 requests per day for any given authentication token
- The One-Frame service is a very robust system with no downtime
- This system only supports scaling up, not scaling out

## Method
- There are 1,440 minutes in a day. Making a request to the One-Frame service every 1.44 minutes will result in 1,000 requests per day. I will create a cron job that fetches all possible currency pairs every 2 minutes (as 1.44 is impractical) and stores the responses in memory as a cache.
- When users make requests, we will retrieve the data from the cache and return it to the user. This approach can handle 10,000 requests per day, and fresh data will be available every 2 minutes.
- I have attempted to handle error cases as much as possible.
- I do not use a *cache-aside* strategy here because if a user queries a pair with no data, it would count towards the One-Frame usage limit. If a user queries that pair 9,999 times, we would exceed the limit.

## Starting this Codebase
1. Ensure your machine has Docker installed
2. Clone this codebase
3. Navigate to the codebase root folder
4. Run `docker-compose up` (You can switch between `dummy` and `live` modes by modifying `FOREX_SERVICE_MODE` in the `docker-compose.yml` file)
5. Testing: `curl "http://localhost:8090/rates?from=USD&to=JPY"`

## Future Improvements
- Use Redis for caching instead of in-memory storage to enable scaling out
- Implement a retry mechanism in case the One-Frame service becomes less robust
- Implement unit tests
- Integrate logging
- Break down `OneFrameLive` into smaller services
