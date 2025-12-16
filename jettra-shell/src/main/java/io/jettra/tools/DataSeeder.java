package io.jettra.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataSeeder {

    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String token;

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: DataSeeder <username> <password> <dbName> <storageEngine> <numInvoices>");
            System.exit(1);
        }

        String username = args[0];
        String password = args[1];
        String dbName = args[2];
        String engine = args[3];
        int numInvoices = Integer.parseInt(args[4]);
        
        // Scale other entities roughly relative to invoices or fixed as per requirements
        int numClients = 50_000;
        int numCategories = 100;
        int numProducts = 10_000;
        
        // For testing, if numInvoices is small, scale down others? No, user asked for fixed numbers.
        // But user asked for 1M invoices.
        
        try {
            login(username, password);
            createDatabase(dbName, engine);
            
            createCollection(dbName, "clientes");
            createCollection(dbName, "categoriasproductos");
            createCollection(dbName, "productos");
            createCollection(dbName, "facturas");
            createCollection(dbName, "facturasdetalles");
            
            System.out.println("Generating data for " + dbName + " (" + engine + ")...");
            
            generateClients(dbName, numClients);
            generateCategories(dbName, numCategories);
            generateProducts(dbName, numProducts, numCategories);
            generateInvoices(dbName, numInvoices, numClients, numProducts);
            
            System.out.println("Seeding completed for " + dbName);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void login(String username, String password) throws Exception {
        Map<String, String> creds = new HashMap<>();
        creds.put("username", username);
        creds.put("password", password);
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(creds)))
                .build();
        
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new RuntimeException("Login failed: " + res.body());
        
        Map<String, Object> map = mapper.readValue(res.body(), Map.class);
        token = (String) map.get("token");
        System.out.println("Login successful.");
    }

    private static void createDatabase(String name, String engine) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("engine", engine);
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/dbs"))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        
        client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Database " + name + " created/verified.");
    }

    private static void createCollection(String db, String col) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("database", db);
        body.put("collection", col);
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/cols"))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        
        client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Collection " + col + " created.");
    }

    private static void generateClients(String db, int count) throws Exception {
        System.out.println("Generating " + count + " clients...");
        ExecutorService executor = Executors.newFixedThreadPool(20); // 20 concurrent threads
        AtomicInteger progress = new AtomicInteger(0);
        
        for (int i = 0; i < count; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", "C" + idx); // Simple ID
                    doc.put("nombre", "Cliente " + idx);
                    doc.put("direccion", "Calle " + idx);
                    doc.put("telefono", "555-" + String.format("%04d", idx));
                    doc.put("email", "cliente" + idx + "@test.com");
                    
                    postDocument(db, "clientes", doc);
                    
                    int p = progress.incrementAndGet();
                    if (p % 1000 == 0) System.out.println("Clients: " + p + "/" + count);
                } catch (Exception e) {
                    System.err.println("Error creating client " + idx);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
    }
    
    private static void generateCategories(String db, int count) throws Exception {
        System.out.println("Generating " + count + " categories...");
        for (int i = 0; i < count; i++) {
             Map<String, Object> doc = new HashMap<>();
             doc.put("id", "CAT" + i);
             doc.put("nombre", "Categoria " + i);
             doc.put("descripcion", "Desc for cat " + i);
             postDocument(db, "categoriasproductos", doc);
        }
    }
    
    private static void generateProducts(String db, int count, int numCats) throws Exception {
        System.out.println("Generating " + count + " products...");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger progress = new AtomicInteger(0);
        Random rand = new Random();
        
        for (int i = 0; i < count; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", "P" + idx);
                    doc.put("nombre", "Producto " + idx);
                    doc.put("categoria", "CAT" + rand.nextInt(numCats));
                    doc.put("precio", 10.0 + rand.nextDouble() * 100.0);
                    doc.put("stock", rand.nextInt(1000));
                    
                    postDocument(db, "productos", doc);
                    
                    int p = progress.incrementAndGet();
                    if (p % 1000 == 0) System.out.println("Products: " + p + "/" + count);
                } catch (Exception e) {
                     System.err.println("Error creating product " + idx);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
    }

    private static void generateInvoices(String db, int count, int numClients, int numProducts) throws Exception {
        System.out.println("Generating " + count + " invoices...");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger progress = new AtomicInteger(0);
        Random rand = new Random();
        
        for (int i = 0; i < count; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String invoiceId = "F" + idx;
                    Map<String, Object> invoice = new HashMap<>();
                    invoice.put("id", invoiceId);
                    invoice.put("fecha", new java.util.Date().toString()); // Use ISO string ideally
                    invoice.put("cliente_id", "C" + rand.nextInt(numClients));
                    invoice.put("total", 0.0); // Will update
                    
                    postDocument(db, "facturas", invoice);
                    
                    // Details (1-5 items)
                    int items = 1 + rand.nextInt(5);
                    double total = 0;
                    for (int k = 0; k < items; k++) {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("factura_id", invoiceId);
                        detail.put("producto_id", "P" + rand.nextInt(numProducts));
                        int qty = 1 + rand.nextInt(10);
                        detail.put("cantidad", qty);
                        double price = 10.0 + rand.nextDouble() * 50.0; // Simplify price lookup
                        detail.put("precio_unitario", price);
                        detail.put("subtotal", qty * price);
                        
                        total += qty * price;
                        
                        postDocument(db, "facturasdetalles", detail);
                    }
                    
                    // Update invoice total? 
                    // Jettra currently overwrite whole doc on save. 
                    invoice.put("total", total);
                    postDocument(db, "facturas", invoice);

                    int p = progress.incrementAndGet();
                    if (p % 1000 == 0) System.out.println("Invoices: " + p + "/" + count);
                } catch (Exception e) {
                     System.err.println("Error creating invoice " + idx);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(24, TimeUnit.HOURS); // Give it time
    }

    private static void postDocument(String db, String col, Map<String, Object> doc) throws Exception {
         HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/doc?db=" + db + "&col=" + col))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(doc)))
                .build();
        
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Post failed: " + res.body());
        }
    }
}
