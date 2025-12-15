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

### Backup Database
```java
String file = client.backupDatabase("my_app_db");
System.out.println("Backup created: " + file);
```

### List Backups
```java
List<String> backups = client.listBackups();
```

## Transaction Management

You can perform multiple operations atomically.

```java
// 1. Begin
String txID = client.beginTransaction();

try {
    // 2. Perform operations passing txID
    client.save("my_app_db", "users", new User("Bob", "bob@example.com"), txID);
    client.deleteDocument("my_app_db", "logs", "old_log_id", txID);

    // 3. Commit
    client.commitTransaction(txID);
} catch (Exception e) {
    // 4. Rollback on error
    client.rollbackTransaction(txID);
}
```

## Collection Operations

### Create Collection
```java
client.createCollection("my_app_db", "users");
```

## Document Operations

### Using Maps (Dynamic Data)

```java
Map<String, Object> user = new HashMap<>();
user.put("username", "jdoe");
user.put("email", "john@example.com");

String id = client.saveDocument("my_app_db", "users", user);
System.out.println("Saved user with ID: " + id);

Map<String, Object> savedUser = client.getDocument("my_app_db", "users", id);
```

### Using Java Records (Type-Safe)

You can use Java Records to automatically map your data to and from the database.

```java
public record User(String username, String email, int age) {}

// ...

User newUser = new User("alice", "alice@example.com", 30);

// Save Record
String id = client.save("my_app_db", "users", newUser);
System.out.println("Saved Record ID: " + id);

// Get Record
User retrievedUser = client.get("my_app_db", "users", id, User.class);
System.out.println("User email: " + retrievedUser.email());

// Query Records
List<User> users = client.query("my_app_db", "users", 10, 0, User.class);
users.forEach(u -> System.out.println(u.username()));
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

## Repository Pattern Example

For larger applications, it is recommended to abstract the database access using the Repository Pattern. Here is a reusable implementation.

### 1. The Generic Repository Interface

```java
public interface CrudRepository<T, ID> {
    ID save(T entity);
    T findById(ID id);
    List<T> findAll(int limit, int offset);
}
```

### 2. The Abstract Implementation

```java
import io.jettra.driver.JettraClient;
import java.util.List;

public abstract class JettraRepository<T> implements CrudRepository<T, String> {
    private final JettraClient client;
    private final String dbName;
    private final String collectionName;
    private final Class<T> entityClass;

    public JettraRepository(JettraClient client, String dbName, String collectionName, Class<T> entityClass) {
        this.client = client;
        this.dbName = dbName;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
        // Ensure collection exists
        client.createCollection(dbName, collectionName);
    }

    @Override
    public String save(T entity) {
        return client.save(dbName, collectionName, entity);
    }

    @Override
    public T findById(String id) {
        return client.get(dbName, collectionName, id, entityClass);
    }

    @Override
    public List<T> findAll(int limit, int offset) {
        return client.query(dbName, collectionName, limit, offset, entityClass);
    }
}
```

### 3. Usage Example

Define your Entity (Record) and Repository:

```java
// Entity
public record Product(String name, double price) {}

// Repository
public class ProductRepository extends JettraRepository<Product> {
    public ProductRepository(JettraClient client) {
        super(client, "ecommerce_db", "products", Product.class);
    }
}
```

Use it in your application:

```java
JettraClient client = new JettraClient("localhost", 8080, "admin", "pwd");
ProductRepository productRepo = new ProductRepository(client);

// Create
String id = productRepo.save(new Product("Laptop", 999.99));

// Read
Product laptop = productRepo.findById(id);
System.out.println("Found: " + laptop.name());
```

## Advanced Patterns

Since JettraDB is a simple document store, specialized features like joins and server-side aggregations are implemented on the client side.

### 1. Document References (Joins)

To model relationships, store the ID of one document in another.

```java
public record Author(String name) {}
public record Book(String title, String authorId) {}

// 1. Create Author
String authorId = client.save("library", "authors", new Author("J.K. Rowling"));

// 2. Create Book referencing Author
client.save("library", "books", new Book("Harry Potter", authorId));

// 3. Fetch Book and "Join" Author
Book book = client.query("library", "books", 1, 0, Book.class).getFirst();
Author author = client.get("library", "authors", book.authorId(), Author.class);

System.out.println("Book: " + book.title() + ", Author: " + author.name());
```

### 2. Client-Side Aggregations

Use Java Streams to perform aggregations on data retrieved from JettraDB.

```java
public record Order(double amount, String status) {}

// Imagine we have many orders in the database
List<Order> orders = client.query("sales", "orders", 1000, 0, Order.class);

// Calculate Total Revenue using Java Streams
double totalRevenue = orders.stream()
    .filter(o -> "COMPLETED".equals(o.status()))
    .mapToDouble(Order::amount)
    .sum();

System.out.println("Total Revenue: $" + totalRevenue);

// Group by Status
Map<String, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::status, Collectors.counting()));

System.out.println("Orders per status: " + countByStatus);
```
