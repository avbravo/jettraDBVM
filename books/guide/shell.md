# Guía del Shell de JettraDB

El Shell de JettraDB es una interfaz de línea de comandos (CLI) interactiva que permite gestionar bases de datos, colecciones, documentos y el estado del cluster.

## Conexión y Autenticación

El Shell de JettraDB ahora soporta dos modos de conexión. Dependiendo del modo, ciertos comandos estarán disponibles o restringidos.

### Modos de Conexión

- **Conectar a un Nodo de Base de Datos**:
  Permite todos los comandos de gestión de datos, usuarios y colecciones.
  ```bash
  jettra> connect node http://localhost:8080
  ```

- **Conectar a un Servidor Federado**:
  Solo permite comandos de información del cluster federado (`federated ...`). Los comandos de bases de datos y colecciones están deshabilitados.
  ```bash
  jettra> connect federated http://localhost:9000
  ```

- **Iniciar sesión (solo en modo nodo)**:
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

## Gestión Federada (Recomendado)

Si el nodo está bajo la gestión de un servidor federado, use los siguientes comandos para gestionar el cluster:

- **Listar servidores federados**:
  ```bash
  jettra> federated show
  ```

- **Ver información del líder federado**:
  ```bash
  jettra> federated leader
  ```

- **Listar nodos de base de datos y líder actual**:
  ```bash
  jettra> federated nodes
  ```

- **Ver detalles del nodo líder de base de datos**:
  ```bash
  jettra> federated node-leader
  ```

- **Agregar un servidor federado (Runtime)**:
  ```bash
  jettra> federated add http://new-federated-node:9000
  ```

- **Detener un servidor federado**:
  ```bash
  jettra> federated stop http://localhost:9000
  ```

- **Remover un peer federado (Raft)**:
  ```bash
  jettra> federated remove http://dead-node:9000
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
