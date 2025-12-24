---
name: Feature Project
about: Full Project roadmap and task list for JettraDBVM
title: '[PROJECT] Full Roadmap and Task List'
labels: project, enhancement
assignees: ''

---

**Is your feature request related to a problem? Please describe.**
The project requires a full, detailed roadmap and task list to track the status of all planned and implemented features.

**Describe the solution you'd like**
Implementation and tracking of all tasks from the `TAREAS.md` file, organized by module and status.

### 🚀 High Priority & Current Features
- [ ] Crear documentos embebidos y referenciados (Interface web, shell, curl, driver).
- [ ] Migrar el proyecto de HelidonSE a Quarkus.
- [ ] Probar el ejemplo de NFL IA con Quarkus.
- [ ] Generar los archivos y ejemplos de docker compose.
- [ ] Usar Bellsoft Native Image (JDK 25).
- [ ] Implementar soporte para tipos de datos estructurados (`_definition`).

### 🧠 JettraMemoryDB
- [ ] Crear la base de datos JettraMemoryDB (Golang KV store).
- [ ] Integrar Memory DB con el servidor federado (Caché + Persistencia asíncrona).
- [ ] Crear la base de datos totalmente en memoria usando JettraDBVM.

### 🔗 Driver & Shell
- [ ] Modificar el driver para interactuar con el servidor federado.
- [ ] Modificar el shell para interactuar con el servidor federado.
- [ ] Modificar el curl para interactuar con el servidor federado.
- [ ] Añadir nuevos comandos al help del shell y documentar `shell.md`.
- [ ] Añadir soporte para ver versiones y restaurar desde shell/curl/driver.

### 🌐 Servidor Federado (Core Management)
- [x] Resolver el problema de NodeID "desconocido" en la UI web.
- [x] Implementar la sincronización de `federated.json` al añadir/remover servidores.
- [x] Asegurar que los nuevos servidores no se conviertan en líderes si ya existe uno.
- [ ] Añadir en la interface gráfica la opción de añadir nuevos servidores federados.
- [x] Implementar comandos en el shell para detener o remover servidores federados.
- [x] Realizar pruebas de failover y persistencia de estado del cluster.
- [x] Documentar el proceso de failover a nivel de servidor y nodos.

### 💾 Almacenamiento & Engine
- [ ] Optimizar `JettraStoreEngine` vs `JettraBasic`.
- [ ] Implementar formato de serialización Java optimizado (eficiente y pequeño).
- [ ] Usar Compact Headers de Java 25 para optimizar el tamaño de los objetos.
- [x] Renombrar `cluster.json` a `federated.json` en toda la infraestructura.

### ✅ Completed Tasks (History)
- [x] Eliminar el proyecto `jettra-federated-shell` (integrado).
- [x] Corregir validación de servidor federado para operaciones de escritura.
- [x] Sincronizar `federated.json` en servidores activos e inactivos.
- [x] Implementar comando `federated show`, `leader`, `nodes`, `node-leader`.
- [x] Permitir login en modo federado.
- [x] Implementar redirecciones automáticas al nodo líder desde el proxy.
- [x] Documentar que sin servidor federado los nodos operan en modo simple (solo lectura).
- [x] Implementar sincronización de `FederatedServers` en nodos de base de datos con Hot-Reload automático al detectar cambios.
- [x] Actualizar `shell.md` con ejemplos de todos los comandos.
- [x] Usar diálogos Flowbite para confirmaciones (índices, backups, borrar DB).
- [x] Crear e integrar la interfaz web del servidor federado.
- [x] Implementar algoritmos de consenso Raft para gestión de líderes.
- [x] Mostrar métricas de consumo (CPU, RAM, Disco, Latencia) por nodo.
- [x] Implementar soporte para versiones de documentos (LSM-style).
- [x] Añadir historial y autocompletado en el shell.
- [x] Generar imágenes Docker, Postman y Nativa.
- [x] Implementar soporte para DataFrame-EC y Eclipse Collections.
- [x] Crear base de datos `_system` y colección `_users` para seguridad.
- [x] Soporte para exportación/importación CSV y JSON.
- [x] Implementar seguridad RBAC (admin, reader, owner, writer/reader).
- [x] Soporte para transacciones (Rollback/Commit).
- [x] Crear herramientas de Backup (`jettraBackup`) y Restore (`jettraRestore`).
- [x] Desarrollar Driver Java con soporte para Java Records y Repositorios.
- [x] Implementar Dashboard responsivo con Flowbite CSS.

### 🔧 Automation & Startup
- [ ] Crear `config.json` automáticamente si no existe.
- [ ] Crear directorio `data` y base de datos `_system` al iniciar.
- [x] Implementar un loop de reinicio automático en `run.sh` (exit code 3).
- [ ] Verificar estado de consistencia multicapa al arrancar.

**Describe alternatives you've considered**
Maintaining a separate `.md` file was useful, but integrating it into the Issue Tracking system allows for better collaboration and external visibility.

**Additional context**
This list reflects the current state of the JettraDBVM project as of December 24, 2025. It covers the evolution from a simple storage engine to a complex federated and distributed database system.
