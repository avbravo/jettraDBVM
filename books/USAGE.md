# Guía de Uso de JettraDBVM

JettraDBVM es la versión portada a Java (Helidon SE) de JettraDB. A continuación se detalla cómo construir, ejecutar y utilizar la aplicación.

## Requisitos Previos

- Java 21 o superior
- Maven 3.8+

## Construcción

Para compilar el proyecto y generar el artefacto ejecutable:

```bash
cd jettraDBVM
mvn package -DskipTests
```

Esto generará el archivo `target/jettraDBVM-1.0-SNAPSHOT.jar` y copiará las dependencias a `target/libs`.

## Ejecución

Para iniciar el servidor:

```bash
java -jar target/jettraDBVM-1.0-SNAPSHOT.jar
```

El servidor iniciará por defecto en `http://localhost:8080`.

## Autenticación y Seguridad

El sistema utiliza **Basic Authentication**. Todas las peticiones deben incluir las credenciales.

- **Usuario por defecto:** `admin`
- **Contraseña por defecto:** `adminadmin`
- **Rol:** `admin` (Superusuario)

Los usuarios y roles se almacenan en la colección `_users` de la base de datos `_system` (para usuarios globales) o de cada base de datos específica.

**Ejemplo:**
```bash
curl -u admin:adminadmin "http://localhost:8080/..."
```

## Uso de la API

Actualmente, el sistema soporta operaciones básicas de almacenamiento de documentos. Todos los endpoints requieren autenticación.

### 1. Verificar Estado

**Endpoint:** `GET /`

```bash
curl -u admin:adminadmin http://localhost:8080/
```
**Respuesta:** `JettraDBVM is running!`

### 2. Guardar un Documento

**Endpoint:** `POST /db/doc`

**Parámetros Query:**
- `db`: Nombre de la base de datos (e.g., `testdb`)
- `col`: Nombre de la colección (e.g., `users`)

**Body:** JSON del documento a guardar.

**Ejemplo:**
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/db/doc?db=testdb&col=users" \
     -H "Content-Type: application/json" \
     -d '{"name": "Juan Perez", "email": "juan@example.com", "age": 30}'
```

**Respuesta:** `Saved with ID: <uuid-generado>`

### 3. Consultar un Documento por ID

**Endpoint:** `GET /db/doc`

**Parámetros Query:**
- `db`: Nombre de la base de datos
- `col`: Nombre de la colección
- `id`: ID del documento

**Ejemplo:**
```bash
curl -u admin:adminadmin "http://localhost:8080/db/doc?db=testdb&col=users&id=<uuid>"

curl -u admin:adminadmin "http://localhost:8080/db/doc?db=testdb&col=users&id=a99c6201-0e5d-404e-967f-21689cfbc557"
```

**Respuesta:** JSON del documento.

### 4. Consultar Todos los Documentos

**Endpoint:** `GET /db/all`

**Parámetros Query:**
- `db`: Nombre de la base de datos
- `col`: Nombre de la colección
- `limit` (opcional): Límite de resultados (default 100)
- `offset` (opcional): Desplazamiento (default 0)

**Ejemplo:**
```bash
curl -u admin:adminadmin "http://localhost:8080/db/all?db=testdb&col=users"
```

**Respuesta:** Lista JSON de documentos `[{}, {}]`.

### 5. Verificar Almacenamiento

Los datos se guardan en el directorio `data/` dentro del directorio de trabajo.
Estructura: `data/<db>/<col>/<id>.jdb`

Ejemplo: `data/testdb/users/<uuid>.jdb` (Formato binario CBOR)

### 6. Eliminar un Documento

**Endpoint:** `DELETE /db/doc`

**Query:** `db`, `col`, `id`

**Ejemplo:**
```bash
curl -u admin:adminadmin -X DELETE "http://localhost:8080/db/doc?db=testdb&col=users&id=<uuid>"
```

### 7. Eliminar Todos los Documentos (Vaciar Colección)

**Endpoint:** `DELETE /db/all`

**Query:** `db`, `col`

### 8. Actualizar un Documento

**Endpoint:** `PUT /db/doc`

**Query:** `db`, `col`, `id`
**Body:** JSON con los nuevos datos.

**Ejemplo:**
```bash
curl -u admin:adminadmin -X PUT "http://localhost:8080/db/doc?db=testdb&col=users&id=<uuid>" -H "Content-Type: application/json" -d '{"name": "Juan Updated"}'
```

### 9. Insertar Múltiples Documentos

**Endpoint:** `POST /db/docs`

**Query:** `db`, `col`
**Body:** Array JSON `[{}, {}]`

**Ejemplo:**
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/db/docs?db=testdb&col=users" -H "Content-Type: application/json" -d '[{"name":"A"}, {"name":"B"}]'
```

### 10. Actualizar Múltiples Documentos

**Endpoint:** `PUT /db/docs`

**Query:** `db`, `col`
**Body:** Array JSON con los documentos a actualizar (deben incluir `_id` o `id`).

### 11. Gestión de Índices

#### Crear Índice
**Endpoint:** `POST /db/index`
**Query:** `db`, `col`
**Body:** `{"fields": ["email"], "unique": true, "sequential": false}`

**Ejemplo:**
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/db/index?db=testdb&col=users" -H "Content-Type: application/json" -d '{"fields": ["email"], "unique": true}'
```

#### Eliminar Índice
**Endpoint:** `DELETE /db/index`
**Query:** `db`, `col`
**Body:** `{"fields": ["email"]}`

#### Listar Índices
**Endpoint:** `GET /db/index`
**Query:** `db`, `col`

**Ejemplo:**
```bash
curl -u admin:adminadmin "http://localhost:8080/db/index?db=testdb&col=users"
```
