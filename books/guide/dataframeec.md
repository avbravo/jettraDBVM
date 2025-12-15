# DataFrame EC Support

JettraDB now supports **Dataframe-EC** for efficient data processing.

## Server-Side Integration
The Jettra Server includes `dataframe-ec` libraries.
API Helper implementation:
- `GET /api/dataframe/stat` - Checks support status.

## Usage in Java Driver
The `jettra-driver` includes the `dataframe-ec` dependency. You can fetch data using the driver and load it into a DataFrame for client-side processing.

Example (Java):
```java
List<Map<String, Object>> data = client.find("my_db", "my_col");
DataFrame df = new DataFrame("MyData");
// populate df from data
```
