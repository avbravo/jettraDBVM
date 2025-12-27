# Jettra Memory Driver
 
The `jettra-memory-driver` is a high-performance network client for interacting with the `jettra-memory` server. It provides a familiar API for database, collection, and document operations, as well as support for transactions and versioning.
 
## Installation
 
Add the following dependency to your `pom.xml`:
 
```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettra-memory-driver</artifactId>
    <version>1.0.0</version>
</dependency>
```
 
## Usage Examples
 
### Connecting to the Server
 
```java
import io.jettra.memory.driver.JettraMemoryDriver;
 
JettraMemoryDriver driver = new JettraMemoryDriver("http://localhost:9090");
driver.connect("admin", "adminadmin");
```
 
### Database and Collection Operations
 
```java
// Create a database
driver.createDatabase("myDB");
 
// Create a collection
driver.createCollection("myDB", "users");
 
// List collections
List<String> collections = driver.listCollections("myDB");
```
 
### Document CRUD
 
```java
Map<String, Object> user = new HashMap<>();
user.put("name", "John Doe");
user.put("email", "john@example.com");
 
// Save document
driver.saveDocument("myDB", "users", user);
 
// Get document
Map<String, Object> savedUser = driver.getDocument("myDB", "users", "some-id");
 
// Querying
List<Map<String, Object>> results = driver.query("myDB", "users", Map.of("name", "John Doe"));
```
 
### Transactions
 
```java
driver.beginTransaction();
try {
    driver.saveDocument("myDB", "users", user1);
    driver.saveDocument("myDB", "users", user2);
    driver.commitTransaction();
} catch (Exception e) {
    driver.rollbackTransaction();
}
```
 
### Versioning
 
```java
// Get versions of a document
List<Map<String, Object>> versions = driver.getVersions("myDB", "users", "some-id");
 
// Restore to a specific version
driver.restoreVersion("myDB", "users", "some-id", 1);
```
