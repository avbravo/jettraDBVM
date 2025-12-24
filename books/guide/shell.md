# Guía del Shell de JettraDB

El Shell de JettraDB es una interfaz de línea de comandos (CLI) interactiva que permite gestionar bases de datos, colecciones, documentos y el estado del cluster.

## Conexión y Autenticación

Por defecto, el shell intenta conectarse a `http://localhost:8080`.

- **Conectar a un servidor específico**:
  ```bash
  jettra> connect http://192.168.1.50:8080
  ```

- **Iniciar sesión**:
  ```bash
  jettra> login admin adminadmin
  ```

## Gestión de Usuarios

Permite crear usuarios con diferentes niveles de acceso.

- **Crear usuario (interactivo)**:
  ```bash
  jettra> create user carlo
  ```
  *El shell solicitará la contraseña, el rol (admin, owner, writereader, reader) y las bases de datos permitidas.*

## Navegación y Estructura

- **Listar bases de datos**:
  ```bash
  jettra> show dbs
  ```

- **Seleccionar una base de datos**:
  ```bash
  jettra> use ventas
  ```

- **Listar colecciones**:
  ```bash
  jettra> show collections
  ```

- **Crear una base de datos**:
  ```bash
  jettra> create db inventario
  jettra> create db analytics engine JettraEngineStore
  ```

- **Crear una colección**:
  ```bash
  jettra> create col productos
  ```

## Manipulación de Datos

- **Insertar un documento**:
  ```bash
  jettra> insert productos {"id": "p1", "nombre": "Laptop", "precio": 1200}
  ```

- **Contar documentos**:
  ```bash
  jettra> count productos
  ```

- **Buscar documentos (con paginación)**:
  ```bash
  jettra> find productos
  ```
  *Durante la búsqueda, puede usar [N]ext, [B]ack, [F]irst, [L]ast y [Q]uit.*

- **Eliminar un documento**:
  ```bash
  jettra> delete productos p1
  ```

## Transacciones (ACID)

JettraDB soporta transacciones para asegurar la atomicidad de las operaciones.

- **Iniciar transacción**:
  ```bash
  jettra> begin
  ```

- **Confirmar cambios**:
  ```bash
  jettra> commit
  ```

- **Descartar cambios**:
  ```bash
  jettra> rollback
  ```

## Backup, Exportación e Importación

- **Crear backup de la base de datos actual**:
  ```bash
  jettra> backup
  jettra> backup ventas
  ```

- **Restaurar desde un archivo**:
  ```bash
  jettra> restore /path/to/backup_ventas.zip ventas_recuperada
  ```

- **Exportar colección**:
  ```bash
  jettra> export productos json productos.json
  jettra> export productos csv productos.csv
  ```

- **Importar colección**:
  ```bash
  jettra> import productos json productos.json
  ```

## Versionado de Documentos

Si la colección tiene habilitado el versionado, puede gestionar el historial.

- **Ver historial de versiones**:
  ```bash
  jettra> history productos p1
  ```

- **Ver contenido de una versión específica**:
  ```bash
  jettra> show version productos p1 1735001234567
  ```

- **Revertir a una versión anterior**:
  ```bash
  jettra> revert productos p1 1735001234567
  ```

## Gestión del Cluster

Comandos para administrar nodos en un cluster distribuido.

- **Ver estado del cluster**:
  ```bash
  jettra> cluster status
  ```

- **Añadir un nodo**:
  ```bash
  jettra> cluster add http://localhost:8081
  ```

- **Eliminar un nodo**:
  ```bash
  jettra> cluster remove http://localhost:8081
  ```

- **Pausar/Reanudar sincronización**:
  ```bash
  jettra> cluster pause node2
  jettra> cluster resume node2
  ```

## Estado Federado

Si el nodo está bajo la gestión de un servidor federado:

- **Ver estado federado**:
  ```bash
  jettra> federated
  ```

## Comandos Directos (JQL / SQL)

El shell permite ejecutar comandos directos que son procesados por el motor de consultas.

- **Consultas estilo JQL**:
  ```bash
  jettra> find in productos where precio > 1000
  ```

- **Inserciones directas**:
  ```bash
  jettra> insert into productos values {"id": "p2", "nombre": "Mouse"}
  ```

## Otros Comandos

- **Limpiar pantalla**: `clear` o `cls`
- **Ayuda**: `help`
- **Salir**: `exit` o `quit`
