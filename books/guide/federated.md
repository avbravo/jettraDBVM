# Servidor Federado JettraDB

El Servidor Federado ("Federated Server") es un componente de arquitectura avanzado de JettraDB diseñado para gestionar clusters de bases de datos distribuidas. Actúa como una autoridad centralizada (pero redundante) que supervisa y coordina los nodos de base de datos.

## Objetivo

El objetivo principal del Servidor Federado es desacoplar la lógica de elección de líder y gestión de topología de los nodos de datos individuales, proporcionando una capa de orquestación más robusta y controlable.


## Gestión Dinámica de Servidores Federados

JettraDB soporta la expansión y contracción dinámica del cluster de servidores federados sin necesidad de detenciones manuales complejas. La red utiliza un mecanismo de **Auto-Descubrimiento y Propagación de Configuración**.

### Adición de un Nuevo Servidor (Ejemplo fed-4)

Supongamos que tenemos una red activa con tres servidores (`fed-1`, `fed-2`, `fed-3`) configurados en los puertos 9000, 9001 y 9002. Para añadir un cuarto servidor (`fed-4`) en el puerto 9003:

1.  **Configuración Inicial del Nuevo Nodo**: El nuevo servidor `fed-4` se inicia con una configuración mínima de peers que incluya al menos un servidor activo conocido:
    ```json
    {
        "NodeID": "fed-4",
        "Port": 9003,
        "FederatedServers": [
            "http://localhost:9000",
            "http://localhost:9003"
        ]
    }
    ```
2.  **Solicitud de Unión (Join Cluster)**: Al arrancar, `fed-4` contacta a `fed-1` enviando una solicitud de unión.
3.  **Redirección al Líder**: Si `fed-1` no es el líder actual del cluster federado, responderá con una redirección indicando quién es el líder actual. `fed-4` realizará entonces la petición al líder.
4.  **Validación y Registro en el Líder**: El Líder Federado recibe la URL de `fed-4`, verifica que no exista en su lista actual e inserta el nuevo nodo en su archivo `federated.json` local.
5.  **Propagación Global (Sync)**: El líder propaga la nueva lista completa de servidores (`fed-1, fed-2, fed-3, fed-4`) a todos los seguidores mediante los latidos (*heartbeats*) de Raft.
6.  **Hot-Reload**: Cada nodo receptor (seguidores y el nuevo integrante) detecta el cambio en la lista de pares, actualiza su archivo `federated.json` en caliente y reinicia sus conexiones internas para reconocer al nuevo miembro.

### Eliminación de un Servidor

El proceso de eliminación sigue una lógica inversa:
*   Se solicita al Líder Federado la eliminación de un peer mediante el comando `federated remove <url>`.
*   El líder actualiza su configuración local, excluyendo la URL indicada.
*   La nueva lista reducida se propaga automáticamente a todos los demás servidores.
*   Los servidores restantes actualizan su configuración local y dejan de intentar conectar con el nodo eliminado.

### Sincronización en Nodos de Base de Datos

Los nodos de base de datos también participan en la red de sincronización para asegurar que siempre puedan contactar con los servidores federados disponibles, incluso si la topología de estos últimos cambia.

#### Ejemplo de Flujo de Sincronización:

Supongamos que un nodo de base de datos (`node-1`) tiene en su `config.json`:
```json
{
  "NodeID": "node-1",
  "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001"
  ]
}
```

Si el cluster federado se expande para incluir un tercer servidor (`http://localhost:9002`), el proceso es el siguiente:

1.  **Reporte**: En el siguiente latido (*heartbeat*), `node-1` envía su lista de 2 servidores al servidor federado.
2.  **Verificación**: El servidor federado detecta que la lista de `node-1` es incompleta (faltan servidores).
3.  **Respuesta Autorativa**: El servidor federado devuelve la lista completa de 3 servidores en la respuesta de registro/heartbeat.
4.  **Persistencia y Reinicio**: 
    *   `node-1` recibe la lista y actualiza su archivo `config.json` local.
    *   `node-1` gatilla un **Hot-Reloaded** (reinicio automático con código de salida 3).
    *   Al reiniciarse, `node-1` ya conoce a los 3 servidores federados y tiene mayor redundancia.

Este mecanismo garantiza la coherencia total de la infraestructura sin que el administrador tenga que editar manualmente el archivo `config.json` de cada nodo de datos cuando la capa federada cambia.

#### Estrategias de Hot-Reload por Entorno:

El nodo de base de datos implementa una lógica inteligente de reinicio según dónde se esté ejecutando:

*   **Script de Supervisión (`run.sh`)**: El script incluido en el proyecto contiene un bucle `while true`. Si el nodo detecta que está corriendo bajo este script (vía la variable `JETTRAMODE=SUPERVISED`), sale con el código `3` y el script lo reinicia automáticamente tras 2 segundos.
*   **Contenedores (Docker / Kubernetes)**: El nodo detecta el entorno de contenedor y sale con el código `3`. Se espera que la política de reinicio del orquestador (`restart: always` en Docker o `RestartPolicy` en K8s) levante la nueva instancia.
*   **Modo Standalone (JAR)**: Si se ejecuta manualmente con `java -jar` sin supervisor, el nodo intenta realizar un **Self-Relaunch** (auto-lanzamiento) de un nuevo proceso idéntico antes de cerrar el actual.
*   **Imagen Nativa**: Se comporta igual que en contenedores, saliendo con código `3` para que un supervisor externo gestione el ciclo de vida.

---

## Funciones Principales
1.  **Enrutador Interno**: Dirige a los clientes y nodos hacia el Líder actual.
2.  **Consenso y Elección**: Utiliza un algoritmo de consenso (Raft) entre servidores federados para mantener una alta disponibilidad del servicio de gestión.
3.  **Gestión de Nodos**: Supervisa la salud de los nodos de base de datos (heartbeats) y asigna roles (Líder/Seguidor).
4.  **Recuperación ante Fallos**: Detecta caídas de nodos de datos y reasigna el liderazgo automáticamente. Si un servidor federado cae, otro toma su lugar.

## Implementación

El proyecto `jettra-federated` implementa este servidor. Es una aplicación Java independiente que corre en paralelo al servidor JettraDB (`jettra-server`).

### Arquitectura

*   **FederatedEngine**: El motor central que mantiene el estado del cluster (lista de nodos, quién es líder).
*   **FederatedRaftNode**: Implementación de Raft para que los servidores federados acuerden quién es el "Líder Federado". Solo el Líder Federado toma decisiones sobre el cluster de base de datos.
*   **FederatedService**: API REST para comunicación con clientes, nodos y otros servidores federados.

### Configuración del Cluster Federado

Cada servidor federado utiliza un archivo `federated.json` para definir su identidad y la de sus pares. A continuación, se muestra cómo configurar un cluster de 3 servidores:

**Servidor 1 (`federated.json`):**
```json
{
  "Mode": "federated",
  "NodeID": "fed-1",
  "Port": 9000,
  "FederatedServers": [
    "http://192.168.1.10:9000",
    "http://192.168.1.11:9001",
    "http://192.168.1.12:9002"
  ]
}
```

**Servidor 2 (`federated.json`):**
```json
{
  "Mode": "federated",
  "NodeID": "fed-2",
  "Port": 9001,
  "FederatedServers": [
    "http://192.168.1.10:9000",
    "http://192.168.1.11:9001",
    "http://192.168.1.12:9002"
  ]
}
```

**Servidor 3 (`federated.json`):**
```json
{
  "Mode": "federated",
  "NodeID": "fed-3",
  "Port": 9002,
  "FederatedServers": [
    "http://192.168.1.10:9000",
    "http://192.168.1.11:9001",
    "http://192.168.1.12:9002"
  ]
}
```

#### Explicación de los Parámetros:

*   **`Mode`**: Debe ser siempre `"federated"` para que el proceso arranque con la lógica de gestión de cluster y no como un nodo de datos.
*   **`NodeID`**: Identificador único de este servidor federado. Es vital para el algoritmo de votación Raft.
*   **`Port`**: El puerto de red donde el servidor federado escuchará peticiones.
*   **`FederatedServers`**: Lista completa de todas las direcciones de los servidores federados que componen el cluster de alta disponibilidad. Todos los servidores del grupo deben tener la misma lista.
*   **`Bootstrap`** (Opcional): Si se establece en `true`, el servidor asumirá el rol de **LEADER** inmediatamente al iniciar, sin esperar a que otros nodos se unan o a que expire el timeout de elección. Es útil para inicializar clusters nuevos o para entornos de un solo nodo federado.

### Funcionamiento

1.  **Arranque**: Al iniciar, los servidores federados se comunican entre sí y eligen un Líder Federado usando Raft.
2.  **Registro**: Los nodos JettraDB se registran contra el cluster federado (`/federated/register`).
3.  **Asignación**: El Líder Federado valida los nodos y, si no hay líder de base de datos, asigna uno.
4.  **Sincronización**: El Líder Federado envía comandos (`cluster_register`, `cluster_update_node`) al Líder de Base de Datos para asegurar que la colección `_system._clusternodes` esté sincronizada en todos los nodos de datos.
5.  **Failover**:
    *   **Fallo de Nodo DB**: El Federado detecta timeout y promueve otro nodo.
    *   **Fallo de Federado**: Los otros federados eligen un nuevo Líder Federado, que retoma el control.

### Integración Web y Shell

La interfaz web y el shell consultan al servidor federado para descubrir la topología del cluster y conectarse al nodo adecuado.

## Ejecución

Para poner en marcha un cluster gestionado por servidores federados, siga estos pasos:

### 1. Iniciar el Servidor Federado

El servidor federado se ejecuta como una aplicación Java independiente. Asegúrese de tener el archivo JAR compilado (normalmente ubicado en `jettra-federated/target/jettraFederated.jar`).

#### Ejecución con Java:
```bash
# Iniciar Servidor Federado en puerto 9000 con ID "fed-1"
java -jar jettraFederated.jar 9000 fed-1

# Iniciar forzando el modo Bootstrap (asumir liderazgo inmediatamente)
java -jar jettraFederated.jar 9000 fed-1 -bootstrap
```

#### Ejecución con Docker:
Puede ejecutar el servidor federado dentro de un contenedor Docker mapeando el puerto y el archivo de configuración:

```bash
docker run -d \
  -p 9000:9000 \
  -v $(pwd)/federated.json:/app/federated.json \
  --name jettra-fed-1 \
  jettra/federated:latest
```

Si desea levantar múltiples servidores federados para alta disponibilidad, ejecútelos en puertos distintos (ej. 9000, 9001, 9002) y asegúrese de que sus archivos `federated.json` reflejen sus pares.

### 2. Configurar Nodos de Base de Datos

Cada nodo JettraDB debe configurarse para conocer la dirección del servidor federado. Para garantizar la **Alta Disponibilidad**, se deben incluir **todos** los servidores federados en la lista `FederatedServers`. El nodo intentará contactar con cada uno en orden hasta encontrar el líder actual.

Edite su archivo `config.json` para incluir la entrada `FederatedServers`:

**Configuración para el Nodo de Base de Datos (ej. node1):**
```json
{
  "NodeID": "node1",
  "Port": 8080,
  "DataDir": "data1",
  "distributed": true,
  "Bootstrap": true,
  "FederatedServers": [
    "http://192.168.1.10:9000",
    "http://192.168.1.11:9001",
    "http://192.168.1.12:9002"
  ]
}
```

*Nota: Se recomienda listar todos los nodos federados. Si uno falla, el sistema de base de datos buscará automáticamente al siguiente servidor disponible.*

### 3. Iniciar Nodos de Base de Datos

Una vez configurados, inicie los nodos JettraDB usando el archivo JAR compilado. Ellos se registrarán automáticamente con el servidor federado al arrancar.

#### Ejecución con Java:
```bash
# Iniciar Nodo 1
java -jar jettraDBVM.jar data1/config.json

# Iniciar Nodo 2
java -jar jettraDBVM.jar data2/config.json
```

#### Ejecución con Docker:
```bash
# Iniciar Nodo 1 en contenedor
docker run -d \
  -p 8080:8080 \
  -v $(pwd)/data1:/app/data \
  --name jettra-node-1 \
  jettra/server:latest /app/data/config.json
```

### 4. Verificación

Puede verificar el estado del cluster federado accediendo a:

```
GET http://localhost:9000/federated/status
```

O desde la interfaz web de JettraDB accediendo a la sección "Federated" en la barra lateral.

## Interfaz de Gestión Federativa Avanzada

JettraDB proporciona una interfaz visual rica para la gestión de clusters federados, disponible tanto en el servidor federado (Dashboard directo) como integrada en cada nodo de base de datos.

### Características del Dashboard

1.  **Monitoreo de Salud en Tiempo Real**: Visualice el estado (ACTIVE/INACTIVE) de cada nodo. Los nodos inactivos se marcan claramente y sus métricas se reinician para reflejar la pérdida de conexión.
2.  **Identificación de Liderazgo**: 
    *   **DB Cluster Leader**: El nodo de base de datos que actualmente gestiona las operaciones de escritura.
    *   **Federated Leader**: El servidor federado que actualmente tiene el mando del cluster Raft.
3.  **Métricas de Rendimiento (Real-Time Metrics)**: Cada nodo envía automáticamente sus estadísticas vitales al servidor federado, permitiendo monitorear:
    *   **CPU Usage**: Porcentaje de uso de CPU en el nodo.
    *   **RAM Usage**: Memoria RAM consumida por la instancia de JettraDB.
    *   **Disk Usage**: Espacio en disco utilizado en el directorio de datos.
4.  **Navegación Intuitiva**: Las URLs de los nodos y servidores son **hipervínculos clicleables** que permiten abrir rápidamente la interfaz web de cualquier nodo de la red en una nueva pestaña del navegador.
5.  **Gestión de Topología**: Permite añadir nuevos servidores federados mediante diálogos estilizados con **Flowbite CSS**, así como detener o reiniciar nodos de datos de manera remota.

### Visualización de Métricas

La tabla de "Managed DB Nodes" incluye columnas dedicadas para CPU, RAM y Disco. Estos datos se actualizan dinámicamente cada vez que el nodo envía un latido al servidor federado (cada 5 segundos).

| Node ID | URL | Status | CPU | RAM | Disk | Last Seen |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| node-1 | http://localhost:8080 | ACTIVE | 12% | 245MB | 1.2GB | 16:52:10 |

## Interfaz Web de Gestión

El Servidor Federado incluye un panel de control avanzado para supervisar el estado del cluster en tiempo real.

### Acceso
1. Inicie el servidor federado (por defecto en el puerto 9000).
2. Abra su navegador en `http://localhost:9000`.

### Autenticación e Inicio de Sesión
Para proteger el acceso al dashboard, el servidor requiere autenticación:
*   **Usuario inicial**: `admin`
*   **Contraseña inicial**: `adminadmin`

### Seguridad y Cambio de Contraseña
El servidor utiliza un registro persistente para las credenciales en el archivo `federated_users.json`. Para cambiar la contraseña:
1. Inicie sesión en el dashboard.
2. Haga clic en el icono de la llave (**Manage Security**) en la esquina superior derecha.
3. Complete el formulario de cambio de contraseña.
4. La nueva configuración se aplicará inmediatamente y se guardará de forma permanente.

## Monitoreo de Salud de Nodos

El servidor federado supervisa constantemente los nodos de base de datos registrados. 

*   **Heartbeats**: Los nodos envían señales de vida periódicamente.
*   **Estado Activo/Inactivo**: Si un nodo no envía un heartbeat en 10 segundos, su estado cambiará a **INACTIVE** en el Dashboard.
*   **Reelección Automática**: Si el nodo inactivo era el Líder de la base de datos, el Servidor Federado promoverá automáticamente a otro nodo activo como nuevo líder para garantizar la continuidad del servicio.

> **Importante**: Si no existe ningún servidor federado activo o accesible, todos los nodos de base de datos configurados en modo federado permanecerán en estado de espera ("nodos simples"). En esta situación, **no se asignará ningún líder de base de datos** ni se realizarán operaciones de sincronización de topología hasta que un servidor federado sea ejecutado y pueda gestionar las conexiones y el mando del cluster. Las operaciones de escritura (Bases de datos, Colecciones, Documentos, Índices y Transacciones) devolverán el siguiente error:
> `503 Service Unavailable: No federated server available to assign a leader. Write operations are disabled.`

### Alta Disponibilidad del Servidor Federado (Failover)

Para garantizar que el sistema de gestión sea siempre accesible, JettraDB soporta múltiples servidores federados corriendo en modo de alta disponibilidad (Raft).

### Configuración de Múltiples Federados

Para configurar 3 servidores federados, cada instancia debe conocer la lista completa de sus pares. Esto se puede hacer vía `federated.json` o mediante argumentos de línea de comandos.

#### Ejemplo con argumentos de línea de comandos:

```bash
# Servidor Federado 1 (Puerto 9000)
java -jar jettraFederated.jar 9000 fed-1 http://localhost:9001 http://localhost:9002

# Servidor Federado 2 (Puerto 9001)
java -jar jettraFederated.jar 9001 fed-2 http://localhost:9000 http://localhost:9002

# Servidor Federado 3 (Puerto 9002)
java -jar jettraFederated.jar 9002 fed-3 http://localhost:9000 http://localhost:9001
```

### Proceso de Failover Federado

1.  **Elección de Líder**: Al arrancar, los 3 servidores inician una elección. Solo uno de ellos se convertirá en `LEADER`.
2.  **Gestión Centralizada**: Solo el servidor federado con estado `LEADER` toma decisiones sobre el cluster de base de datos (promover líderes de DB, registrar nodos, etc.). Los otros servidores actúan como `FOLLOWER`.
3.  **Detección de Fallo**: Si el servidor federado `LEADER` se detiene o pierde conexión, los servidores `FOLLOWER` restantes detectan la ausencia de latidos tras un timeout (3-5 segundos).
4.  **Nueva Elección**: Uno de los seguidores iniciará una nueva elección y se convertirá en the new `LEADER`.
5.  **Continuidad**: El nuevo líder federado asume inmediatamente la gestión del cluster, asegurando que los nodos de base de datos sigan teniendo una autoridad de control activa.

### Resiliencia ante Fallo de Quórum (Solitary Leadership)

En una configuración estándar de Raft, un nodo no puede convertirse en líder si no puede comunicarse con la mayoría de sus pares (quórum). Sin embargo, para mejorar la experiencia de usuario en reinicios manuales o desastres totales, JettraDB incluye un mecanismo de **Liderazgo Solitario Automático**:

1.  **Detección de Aislamiento**: Si un servidor federado arranca y detecta que todos sus pares configurados en `FederatedServers` están caídos (errores de conexión persistentes).
2.  **Auto-promoción**: Tras fallar 3 ciclos de elección consecutivos sin recibir respuesta de ningún par, el servidor asumirá que es el único superviviente y se auto-promocionará a **LEADER**.
3.  **Continuidad**: Esto permite que, si reinicias los servidores uno por uno tras una caída total, el primero que inicies retome el control del cluster sin esperar a los demás.

### Verificación del Failover

Puede verificar qué servidor es el líder consultando el endpoint `/federated/status`:

```bash
curl -s http://localhost:9000/federated/status | grep '"raftState"'
```

Si detiene el proceso del líder actual y espera unos segundos, verá que uno de los otros nodos cambia su `raftState` de `FOLLOWER` a `LEADER`.

### Gestión Automática de Configuración (`federated.json`)

Para asegurar la consistencia del cluster ante reinicios, el Servidor Federado actualiza automáticamente el archivo `federated.json` cuando se detectan cambios en la topología:

1.  **Adición/Eliminación de Nodos**: Cuando se usa `federated add` o `federated remove`, el Líder actualiza su lista de peers y guarda los cambios en disco.
2.  **Propagación**: El Líder envía la nueva configuración a todos los seguidores a través de los latidos (heartbeats).
3.  **Sincronización**: Los seguidores (y los nuevos nodos) detectan el cambio, actualizan su memoria y reescriben su propio `federated.json`.

Esto garantiza que si un nodo se reinicia, siempre recordará a sus pares válidos más recientes, evitando problemas de "cerebro dividido" (split-brain) o liderazgo solitario accidental.

## Uso con Herramientas Cliente

Aunque el Servidor Federado coordina el cluster, las herramientas clientes (Shell, Driver, Curl) generalmente siguen interactuando con los puntos de entrada de los nodos de base de datos, quienes internamente respetan la topología dictada por el federado.

### 1. JettraDB Shell
El Shell se conecta a los nodos de JettraDB (ej. puerto 8080). El nodo gestionará las redirecciones necesarias si el nodo contactado no es el líder.

```bash
# Conectar al nodo local o balanceador
java -jar jettra-shell.jar --url http://localhost:8080

# Ver servidores federados asociados
jettra> federated show

# Ver líder de la infraestructura federada
jettra> federated leader

# Ver topología de nodos y líder de datos
jettra> federated nodes

# Ver detalles del líder de datos
# Ver detalles del líder de datos
jettra> federated node-leader

# Agregar servidor federado
jettra> federated add http://new-federated:9000

# Detener un servidor federado
jettra> federated stop http://fed-node:9000

# Remover un peer federado (Raft)
jettra> federated remove http://old-fed-node:9000
```

### 2. Jettra Driver (Java)
El driver debe inicializarse apuntando a uno o varios nodos del cluster JettraDB.

```java
// Conexión estándar al cluster
JettraClient client = new JettraClient("localhost", 8080, "admin", "password");

// Obtener servidores federados asociados
List<String> fedServers = client.getFederatedServers();

// Obtener estado completo del cluster federado
Map<String, Object> status = client.getFederatedStatus();

// Obtener ID del líder federado
String fedLeader = client.getFederatedLeader();

// Obtener lista de nodos de datos
List<Map<String, Object>> nodes = client.getFederatedNodes();

// Obtener detalles del líder actual de base de datos
Map<String, Object> leader = client.getNodeLeader();
System.out.println("El líder de datos es: " + leader.get("url"));
```

### 3. Curl
Puede utilizar `curl` tanto para operaciones de datos (contra los nodos) como para operaciones de gestión (contra el servidor federado).

**Consultar Estado y Líder del Cluster (vía Servidor Federado):**
```bash
curl http://localhost:9000/federated/status
```
*Respuesta de ejemplo:*
```json
{
  "leaderId": "node1",
  "isFederatedLeader": true,
  "nodes": [...],
  "raftState": "LEADER",
  "raftTerm": 5
}
```

**Comprobar Salud (vía Servidor Federado):**
Puede simular un heartbeat manualmente para depuración:
```bash
curl -X POST "http://localhost:9000/federated/heartbeat?nodeId=node1"
```

**Obtener Líder de Base de Datos (vía Servidor Federado):**
Este endpoint devuelve exclusivamente los detalles del nodo líder actual.
```bash
curl http://localhost:9000/federated/node-leader
```

**Obtener Configuración Federada (vía Nodo de Datos):**
Muestra los servidores federados a los que el nodo está reportando.
```bash
curl http://localhost:8080/api/federated/config
```

**Obtener Estado Completo (vía Proxy del Nodo):**
```bash
curl http://localhost:8080/api/federated
```

**Obtener Líder de Datos (vía Proxy del Nodo):**
```bash
curl http://localhost:8080/api/federated/node-leader
```

## Despliegue con Docker Compose

La forma más sencilla de gestionar un cluster completo (servidores federados + nodos de base de datos) es mediante **Docker Compose**. Esto permite levantar toda la infraestructura con un solo comando y gestiona automáticamente la red interna entre contenedores.

### Ejemplo de `docker-compose.yml`

A continuación, un ejemplo para un entorno con 2 servidores federados y 2 nodos de base de datos:

```yaml
version: '3.8'

services:
  # Servidor Federado 1
  fed1:
    image: jettra/federated:latest
    ports:
      - "9000:9000"
    volumes:
      - ./federated1.json:/app/federated.json
    networks:
      - jettra-net

  # Servidor Federado 2
  fed2:
    image: jettra/federated:latest
    ports:
      - "9001:9001"
    volumes:
      - ./federated2.json:/app/federated.json
    networks:
      - jettra-net

  # Nodo de Base de Datos 1
  node1:
    image: jettra/server:latest
    ports:
      - "8080:8080"
    volumes:
      - ./data1:/app/data
    command: ["/app/data/config.json"]
    depends_on:
      - fed1
      - fed2
    networks:
      - jettra-net

  # Nodo de Base de Datos 2
  node2:
    image: jettra/server:latest
    ports:
      - "8081:8081"
    volumes:
      - ./data2:/app/data
    command: ["/app/data/config.json"]
    depends_on:
      - fed1
      - fed2
    networks:
      - jettra-net

networks:
  jettra-net:
    driver: bridge
```

### Notas sobre Docker Compose:

1.  **Networking**: Dentro de la red `jettra-net`, los contenedores pueden comunicarse usando sus nombres de servicio (ej: `http://fed1:9000`) en lugar de `localhost`. Asegúrese de actualizar sus archivos `config.json` y `federated.json` con estos hostnames.
2.  **Volúmenes**: Es fundamental mapear los archivos de configuración (`federated.json`, `config.json`) y las carpetas de datos (`data/`) para que la información persista si el contenedor se reinicia.
3.  **Comando de Arranque**:
    ```bash
    # Iniciar el cluster
    docker-compose up -d

    # Ver logs del cluster
    docker-compose logs -f
    ```

## Pruebas de Failover Automático

Para validar que su infraestructura de Servidores Federados es capaz de recuperarse ante fallos, puede utilizar el script de prueba incluido `sh/testing/test_federated_failover.sh`.

### ¿Qué hace esta prueba?
1.  **Levanta 3 Servidores Federados** en la misma máquina (puertos 9000, 9001 y 9002).
2.  **Activa el modo Bootstrap** en el primer servidor para garantizar un líder inicial rápido.
3.  **Simula el registro de un nodo de base de datos** (`node-alfa`) para verificar que el líder federado toma el control de la base de datos.
4.  **Simula una caída crítica**: Mata el proceso del líder federado actual.
5.  **Verifica la Reelección**: Comprueba que uno de los 2 seguidores restantes se convierte en `LEADER`.
6.  **Verifica la Persistencia**: Asegura que el nuevo líder recupera el estado del cluster y sigue reconociendo a `node-alfa` como el líder de la base de datos.

### Ejecución de la prueba:
Asegúrese de que no haya otros procesos usando los puertos 9000-9002 y ejecute:

```bash
chmod +x sh/testing/test_federated_failover.sh
./sh/testing/test_federated_failover.sh
```

El script mostrará en tiempo real cómo los nodos detectan la caída y cómo el nuevo mando se establece sin intervención manual.


