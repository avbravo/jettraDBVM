# JettraDB Java Driver Guide

The JettraDB Java Driver provides a simple, fluent API to interact with your JettraDB server from Java applications.

## Installation

Add the `jettra-driver` dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettra-driver</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Getting Started

### Initialize the Client

```java
import io.jettra.driver.JettraClient;

// Connect to localhost:8080 with default credentials
JettraClient client = new JettraClient("localhost", 8080, "admin", "admin");
```

## Database Operations

### List Databases
```java
List<String> dbs = client.listDatabases();
System.out.println("Databases: " + dbs);
```

### Create Database
```java
client.createDatabase("my_app_db");
```

### Delete Database
```java
client.deleteDatabase("my_app_db");
```

## Collection Operations

### Create Collection
```java
client.createCollection("my_app_db", "users");
```

## Document Operations

### Save Document
```java
Map<String, Object> user = new HashMap<>();
user.put("username", "jdoe");
user.put("email", "john@example.com");

String id = client.saveDocument("my_app_db", "users", user);
System.out.println("Saved user with ID: " + id);
```

### Get Document
```java
Map<String, Object> savedUser = client.getDocument("my_app_db", "users", id);
System.out.println("User: " + savedUser);
```

### Query Documents
```java
// List first 10 users
List<Map<String, Object>> users = client.query("my_app_db", "users", 10, 0);
users.forEach(u -> System.out.println(u.get("username")));
```

## Error Handling

All operations throw `io.jettra.driver.DriverException` (a RuntimeException) if something goes wrong (e.g., connection error, 500 server error).

```java
try {
    client.createDatabase("existing_db");
} catch (DriverException e) {
    System.err.println("Error: " + e.getMessage());
}
```
