# Servidor Federado JettraDB

El Servidor Federado ("Federated Server") es un componente de arquitectura avanzado de JettraDB diseñado para gestionar clusters de bases de datos distribuidas. Actúa como una autoridad centralizada (pero redundante) que supervisa y coordina los nodos de base de datos.

## Objetivo

El objetivo principal del Servidor Federado es desacoplar la lógica de elección de líder y gestión de topología de los nodos de datos individuales, proporcionando una capa de orquestación más robusta y controlable.

Sus funciones principales son:
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

### Configuración

El Servidor Federado se configura a través de `cluster.json` en la raíz del proyecto. Las bases de datos continúan utilizando `config.json`.

```json
{
  "Mode": "federated",
  "Port": 9000,
  "FederatedServers": [
    "http://192.168.1.10:9000",
    "http://192.168.1.11:9000",
    "http://192.168.1.12:9000"
  ]
}
```

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

El servidor federado se ejecuta de forma independiente. Puede iniciarlo usando Maven desde el directorio del proyecto:

```bash
# Iniciar Servidor Federado en puerto 9000 con ID "fed-1"
mvn -pl jettra-federated exec:java \
    -Dexec.mainClass="io.jettra.federated.FederatedMain" \
    -Dexec.args="9000 fed-1"
```

Si desea levantar múltiples servidores federados para alta disponibilidad, ejecútelos en puertos distintos (ej. 9000, 9001, 9002) y asegúrese de que sus archivos `cluster.json` o argumentos reflejen sus peers.

### 2. Configurar Nodos de Base de Datos

Cada nodo JettraDB debe configurarse para conocer la dirección del servidor federado. Edite su archivo `config.json` para incluir la entrada `FederatedServers`:

**Configuración para el Nodo Líder (ej. puerto 8080):**
```json
{
  "NodeID": "node1",
  "Port": 8080,
  "DataDir": "data1",
  "distributed": true,
  "Bootstrap": true,
  "FederatedServers": [
    "http://localhost:9000"
  ]
}
```

**Configuración para Nodos Seguidores (ej. puerto 8081):**
```json
{
  "NodeID": "node2",
  "Port": 8081,
  "DataDir": "data2",
  "distributed": true,
  "Bootstrap": false,
  "FederatedServers": [
    "http://localhost:9000"
  ]
}
```

### 3. Iniciar Nodos de Base de Datos

Una vez configurados, inicie los nodos JettraDB normalmente. Ellos se registrarán automáticamente con el servidor federado al arrancar.

```bash
# Iniciar Nodo 1
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data1/config.json

# Iniciar Nodo 2
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data2/config.json
```

### 4. Verificación

Puede verificar el estado del cluster federado accediendo a:

```
GET http://localhost:9000/federated/status
```

O desde la interfaz web de JettraDB accediendo a la sección "Federated" en la barra lateral.

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

## Uso con Herramientas Cliente

Aunque el Servidor Federado coordina el cluster, las herramientas clientes (Shell, Driver, Curl) generalmente siguen interactuando con los puntos de entrada de los nodos de base de datos, quienes internamente respetan la topología dictada por el federado.

### 1. JettraDB Shell
El Shell se conecta a los nodos de JettraDB (ej. puerto 8080). El nodo gestionará las redirecciones necesarias si el nodo contactado no es el líder.

```bash
# Conectar al nodo local o balanceador
java -jar jettra-shell.jar --url http://localhost:8080
```

### 2. Jettra Driver (Java)
El driver debe inicializarse apuntando a uno o varios nodos del cluster JettraDB.

```java
// Conexión estándar al cluster
JettraDriver driver = new JettraDriver("http://localhost:8080");
JettraConnection conn = driver.connect("admin", "admin");
// El driver interactúa con el cluster de forma transparente
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
