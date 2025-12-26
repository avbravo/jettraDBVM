# TASK


java -XX:+UseCompactObjectHeaders -jar jettraFederated.jar

java -XX:+UseCompactObjectHeaders -jar jettraDBVM.jar

java -XX:+UseCompactObjectHeaders -jar jettraDBVMShell.jar


# Benchmarking

./sh/testing/run_benchmark_comparison.sh

# Detener Jettra
pkill -f "jettraDBVM.jar"


Recuperación ante fallos
Consistencia de datos multicapa

Procesamiento de consultas basado en caché:


http://0.0.0.0:6585/

- [] Crear documentos embebidos y referenciados para verificar si se almacenan bien, desde interface web, shell, curl y driver java



# Quarkus

- [] Migrar el proyecto de HelidonSE a Quarkus
- [] Probar el ejemplo de NFL IA con Quarkus




curl -u admin:adminadmin "http://localhost:8080/..."

# Docker 
- [] Generar los archivos y ejemplos de docker compose

# jAVA 25

- [ ] Usar Bellsoft Native Image

[BellSoft](https://bell-sw.com/pages/downloads/#jdk-25-lts)

# Test Avanzado.

- [] Ejecutar un test de federated1 hasta federated5 y de node1 hasta node7. Insertar registros , eliminar registros
simular 500 usuarios simultaneos y guardar el resultado en archivos en book/test/
Guardar resultados de consumo de memoria ram, consumo de cpu, tiempos de respuestas , distribucion de datos.
Cantidad e usuarios simultaneos, has este test para la version de helidon, de golang y quarkus y genera
los resultados para consultarlos en archivos y graficas comparativas y  un analisis para determinar cual es la
mejor implementacion


- [] Apl

# Golang
 -[] Analiza todos los proyectos y crea su contraparte en Goolang, es decir una alternativa a cada uno
pero desarrollado sobre el lenguaje golang, Los proyectos los renombras al final con -golang. Por ejemplo
jettra-server seria jettra-server-golang y debes implementar todas las funcionalidades existentes en el los proyectos.


# Quarkus
 -[] Analiza todos los proyectos y crea su contraparte en Quarkus, es decir una alternativa a cada uno
pero desarrollado sobre el lenguaje quarkus Los proyectos los renombras al final con -quarkus. Por ejemplo
jettra-server seria jettra-server-quarkus y debes implementar todas las funcionalidades existentes en el los proyectos.






# JettraMemoryDB
- [] Crear la base de datos JettraMemoryDB que es una base de datos Key-Value en memoria desarrollada en Golang y que contiene un driver Java
     esta base de datos se usa con el servidor federado para almacenar los datos en memoria , mientras un proceso asincrono 
      se encarga de persistir los datos en el lider y los nodos controlados por el servidor federado.

- [] Integrar la base de datos en Memoria con el servidor federado para que escriba en cache los datos
- [] Crear la base de datos totalmente en memoria y que usa JettraDBVM como motor de almacenamiento

# Driver

- [] Modificar el driver para que interactue con el servidor federado en lugar de las bases de datos directamente
- [] Modificar el shell para que interactue con el servidor federado en lugar de las bases de datos directamente
- [] Modificar el curl para que interactue con el servidor federado en lugar de las bases de datos directamente

# Referencias entre documentos


# Jettra Storage 




# Documentacion


- [ ] Hacer pruebas de  curl y driver que si no hay un servidor federado que indique cual es el nodo de bsae de datos lider los operaciones de insercion actualizcoon y eliminacion a nivel de bases de datos, colecciones  , documentos no se pueden realizar y se debe enviar un mensaje indicando que no hay servidor federado disponible.
y documentarlo



# Servidor Federado



- [] El lider de servidor federado funciona como balanceador es decir los driver y el shell o curl se van a conectar al servidor federado
     y este internamente redirige cada peticion al nodo lider de bases de datos procesa las operaciones y devuelve el resultado
al cliente (driver, shell, curl)
El servidor federado debe conversar la sesion de que cliente hace que solicitud para devolver la respuesta correcta al cliente especifico
de manera que ningun cliente se conecta directamente a la base  de datos.

Tener en cuenta que se va a crear una base de datos en memoria llamada JettraMemoryDB mas adelante 




- [] En el driver la conexion se debe indicar los servidores federados ya sea leyendo el archivo federated.json o pasandolos directamente
en el codigo del driver para abrir la conexion.
El driver internamente verifica el lider de los servidores federados e identifica el lider de los nodos de base de datos
e intermanete se va a comunicar con el lider de los nodos de bases de datos. 
Esto libera al desarrollador de tener que preocuparse por saber cual es el nodo lider.
Tambien puede obteber la lista y el estado de todos los nodos federados.
Crea la documentacion en el archivo driver.md




- [] Anadir los comandos nuevos al help del shell y documentar el archivo shell.md con todos los cambios y ejemplos.

- [] Añadir a la interface grafica la opcion de añadir un nuevo servidor federado se abre un dialogo y se inserta el ip, password y el nombre del servidor

- [] En el shell añadir la opcion de detener un servidor federado
- [] En el shell añadir la opcion de remover un servidor federado



- [] Instale el plugin de excalidraw pero no muestra los diagramas de manera visual










# ---------------------------------------------------------
# Completadas
# ------------------------------------------------------------
# Arranque de un servidor Federado


- [x] El dialogo con el mensaje "Acción Denegada
No puede detener, recargar o eliminar este nodo porque este servidor no es el Líder Federado. Solo el Líder tiene prioridad para ejecutar estas acciones."
no se cierra queda siempre visible



- [x] Enviar el mensaje que no puede detener recargar o eliminar un nodo de base  de datos poque no es un servidor federado lider y no tiene prioridad para ejecutar 
esta accion mostrarlo en un dialogo estilo flowbiew css en la interface web del sistema jettra-federated removerlas desde el formulario
web de jettra-server estas opciones solo deben aparecer en el proyecto jettra-federated en jettra-server no debe
tener las opciones de detener, recargar y actualizar.




- [x] Enviar el mensaje que no puede detener recargar o eliminar un nodo de base  de datos poque no es un servidor federado lider y no tiene prioridad para ejecutar 
esta accion mostrarlo en un dialogo estilo flowbiew css

- [x] En la interface Web del proyecto jettra-federated  añadir la opcion de detener un servidor federado en el panel Estado de consenso
el unico autorizado a detener un servidor federado es el servidor federado lider los demas no podran detenerlos.
y en la seccion Nodos del Cluster del formulario el unico que puede detener recargar o eliminar nodos del cluster de bases de datos
es el servidor federado lider. Los que no son lider no pueden detenerlo


à
- [x] Al ejecutar las aplicaciones se nota un consumo excesivo de memoria RAM tienes algun algoritmo que se puede
implementar para mejorar el consumo de memoria RAM.

- [x] Las estadisticas de memoria Ram de los nodos no muestran los valores reales

- [x] Elimina la opcion del menu Diagrams de la interface web del proyecto  jetrra-server

- [x] El formulario Federated de la interface web de jetrra-server,  en la seccion  superior del formulario Federated Cluster (Peers), 
    colocar en color rojo el status para los servidores INACTIVE. Y PARA EL LIDER marca el estatus UNKNOWN y esta activo debe ser ACTIVE 
   el estatus   que marca.


- [X] El formulario Federated de la interface web de jetrra-server,  en la seccion  superior del formulario Federated Cluster (Peers), 
      muestra todos los servidores federados como esta ACTIVE , y en el ejemplo que uso solo hay uno activo, los demas estan inactivos
      pero aparecen todos como activos aunque esten inactivos.

- [x] El formulario Federated de la interface web de jetrra-server, muestra perfectamente los nodos de la base de datos.
Pero en la seccion  superior del formulario Federated Cluster (Peers), repite el servidor federado con el url http://localhost:9000
y elimine el boton +Add Federated Server ya que no es necesario añadir el servidor desde este formulario.

-  [x] No muestra nada en el formulario  Federated de la interface web de jetrra-server. Debe mostrar el nodo de bases 
de datos actual indicar cual es el lider, mostrar estadisticas metrics de cada nodo y mostrar los servidores federados
y cual es el lider de los servidores federados.

  
  - [x] Para mantener la sincronizacion en todo momento y evitar que un usuario elimine de su configuracion local federated.json o añada uno nuevo sin la autorizacion cuando inicia cada servidor federado
envia la informacion de  "FederatedServers": al lider este verifica con la configuracion local y si no se trata de un nuevo servidor federado ni de uno que fue removido, el servidor federado envia la configuracion al servidor federado este actualiza en local el archivo federated.json, y ocurre el Hot-Realoded.
Esto permite mantener la coherencia de la red de servidores federados.


- [x] Esa logica funciona perfecta para los servidores federados ahora se necesita aplicar la misma logica para los nodos de bases de datos

- [x] cuando un nodo de base de datos se conecta a la red federada. El nodo de base de datos envia al servidor federado su configuracionç
que tiene en el archivo config.json en especial "FederatedServers": , el servidor federado lo compara con "FederatedServers": del archivo
federated.json y devuelve al nodo la lista actualizad de "FederatedServers": , el nodo de base de datos
actualiza su archivo config.json local con el listado actualizado y hace Hot-Reloaded automatico para garantizar que todos 
los nodos tengan actualizado la lista de servidores federados.
Si el listado del nodo de base de datos es identico al del servidor federado no es necesario actualizar el archivo config.json del nodo de base de datos.



- [x] debes implementar el hot-reloaded a nivel del nodo de la base de datos ya que lo deja detenido y no vuelve a ejecutarlo. Recuerda que este se puede ejcutar como un java -jar, o mediante run.sh o mediante imagene nativa o mediante docker o kubernetes por lo que debes tener en cuanta como reiniciar para cada uno. Y recuerda siempre garantizar que se actualice "FederatedServers" con el que posee el lider de los servidores federados



- [x] añadira la opciond e hot-reloaded desde el mismo codigo java es decir que las aplicaciones java soporten reiniciar y arrancar directamente esto se necesita para que los nodos y servidores federados en ciertas condiciones deben reiniciarse, y para no depender de aplicaciones externas para el reinicio sea una funcion dentro de los mismos proyectos



- [x] actualiza la documentacion de todos el proyecto con los ultimos cambios,. y en el proyecto jettra-server en la interface web en el formulario  Federated no se muestra, alli debe mostrar los nodos con sus estatus quien es el nodo lider, y los servidores federados y quien es el servidor federado y la informacion metrixs de cada node
- [x] Cuando no hay ningun servidor federado activo, no se pemiten realizar operaciones de insercion, eliminacion o actualizacion
     de ningun tipo sobre el nodo lider de la base de datos, recuerde que los nodos que no son lideres  no deben realizar estas operaciones
     por lo tanto  se debe indicar que no hay un servidor federado lider y no se pueden realizar operaciones.

- [x] La interface web Dashboard del Cluster del servidor federado la columna url es un hipervinculo y colocar el url como un link de manera que lo abra en otra pagina nueva en el navegador al dar click
- [x] Permite reiniciar los servidores 
- [x] Garantiza la replicacion de la base de datos indices y reglas a los nodos

- [x] Documentar la lógica de gestión dinámica de servidores federados en `federated.md`.

- [x] Eliminar el proyecto jettra-federated-shell ya no se necesita.

- [x] En
- [x] Elimina el proyecto jettra-federated-shell y la documentacion de ese proyecto.

- [x] Implementar sincronización de `FederatedServers` en nodos de base de datos con Hot-Reload automático al detectar cambios.

- [x] Corregir en el shell, curl y driver que si no hay un servidor federado que indique cual es el nodo de bsae de datos lider los operaciones de insercion actualizcoon y eliminacion a nivel de bases de datos, colecciones  , documentos no se pueden realizar y se debe enviar un mensaje indicando que no hay servidor federado disponible.


- [x] Cuando se añade un nuevo servidor federado,  no se deba asignar como lider si ya hay un servidor federado lider y este debe actualizar el archivo federated.json del servidor lider y de todos los servidores 
federados si uno o mas servidores federados esta inactivo al activarse debe sincronizar el archivo federated.json con la lista de servidores federados.
 -[x] Este proceso ocurre cuando se remueve un servidor federado
 -[x] Si un servidor federado inicia y tiene configurado los otros servidores federados en el archico federated.json, debe añadirlo pero no como lider 
si existe un lider activo. Y el algoritmo compara los servidores federados con los establecidos en el archivo federated.json de cada servidor
federado y actualiza el archivo con el nuevo servidor federado para que garantize la integracion.
 -[x] en la interface web debe añadir la opcion de añadir un nuevo servidor federado pero siempre debe ser FOLLOWER o la menos
que no exista un lider añadirlo como lider.



 - [x] El comando federated show  genera el error Error: config.json not found, y deberia mostrar la lista de servidores federados, el estatus y quien es el lider de servidores federados.
 - [x] Añadir la opcion de detener un servidor federado con el comando federated stop <url> este comando debe llevar una pregunta de confirmacion
 - [x] Añadir la opcion de eliminar un servidor federado con el comando federated remove <url> este comando debe llevar una pregunta de confirmacion


- [x] cuando me conecto mediante 
connect federated http://localhost:9000 
e intengo ejecutar login admkn adminadmin
envia el error
Command not available in Federated Mode. Only 'federated' commands are allowed.
debes permiter ejecutra el login para  autenficarse con el servidor federeado 


- [x] Añadir a jettra-shell el comando connect federated que permite conectarse a un servidor federado 
y modificar el antiguo connect
para que sea connect node para conectarse a un nodo de base de datos 
Por lo tanto el comando connect dejara de funcionaar y en su lugar se usar connect node o connect federated pasandole el url del servidor a conectarse
cuando se ejecuta con connect federated no se permite ejectuar los comandos de bases de datos ni colecciones solo estara disponible
 federated show         List federated servers
  federated leader       Show federated leader info
  federated nodes        Show DB nodes and DB leader
  federated node-leader  Show DB leader info

En cambio cuando usa connect node si deben estar disponibles todos los comandos
modifique el help para que considere esta condicion y edite el archivo shell.md para indicar los nuevos cambios



- [x] en jettra-shell el comando 
federated node-leader genera el error
Error: Internal Server Error
y el comando federated leader inclur en la informacion generada el ip y puerto

eleminar los comandos siguiente en el jettra-shell, curl y drive
  cluster status         Show cluster status
  cluster add <url>      Add a new node to the cluster
  cluster remove <url>   Remove a node from the cluster



y añadir la documentacion de estos comandos en el archivo shell.md
 federated show         List federated servers
  federated leader       Show federated leader info
  federated nodes        Show DB nodes and DB leader
  federated node-leader  Show DB leader info


- [x] modifica jettra-shell y añade comandos federated show muesta la lista de los servidores federados a los que esta asociado la base de datos
federated leader  : muestra la inforamcion del lider federado
federated nodes muesta todos los nodos de base de datos y el nodo lider que son parte de la red federada
federated node leader muesta la intormacion del nodo de base de datos lider. 
Incluye estos comandos en el dirver y curl, y crea la documentacion con los ejemplos necesarios


- [x] No permitir accerder a los nodos de bases de datos  si no hay un servidor federado disponible, por lo tanto los driver, curl, shell deben apuntar al servidor federado en lugar de la base  de datos directamente.

- [] Como saber cual servidor federado es el lider y cual nodo es el lider

- [x] añade un comando al jetrra-federaeted-shell llamado node-leader que devuelve el nodo de bsae dde datos lider, este comando tambien debe ser accedido por el driver , curl, y jettra-shel,l de manera que permita identiificar la base de datos lider para ejecutar las operaciones
    
- [x] Actualiza el shell, driver, curl, para que se conecten al servidor federado en lugar de la base de datos directamente y documenta esta opracion en federated.md 
y actualiza los archivos .md que se refieren al tema. y Hacer pruebas con los diversos componentes.



- [x] Documentar que cuando no existe un servidor federado todos los nodos de bases de datos se convierten en nodos y no se asigna ningun lider hasta que el servidor federado sea ejecutado y pueda gestionar las conexions.

- [x] dame un ejemplo de los comandos de jettra-shell y actualiza el archivo shell.md con los ejemplos de cada comando que soporta el shell de la base de datos



- [x] Cuando se crea un indice el mensaje de confirmacion debe ser en un dialogo estilo flowbiewcss y no de javascript estandar


- [x] Cuando se crea un nuevo backup el proceso se realiza perfecto pero no lo agrega a la lista Existing Backups del formulario.





# Servidor Federado


- [x] Crear un shell para el servidor federado que permita administrar los nodos desde la consola el proyecto se debe llamar jettra-federated-shell y debe estar 
a nivel de jettra-server y documentar en el archivo federated.md como se usa. Este shell debe permitir ver todos los servidores federados
su estatus, detener un servidor federado, ver cual es el servidor federado lider y los nodos que pertenecen al servidor federado.


-[x] Una vez dentro, escriba help para ver la lista de comandos disponibles. Por defecto, intentará conectarse a http://localhost:9000.


- [x] Organizar los directorios sh y log 


- [x] El servidor federado lider no replica  a los otros servidores federados que son FOLLOWER el estado de todos los servidores federados 
en la lista Servidores Federados de la interface grafica


- [x] En la interface web del servidor federado mostrar el estatus de todos los servidores federados y el valor "NodeID del servidor federado para que se vea mas elegentantaa.





- [x]en la ultima ejecucion de test se habilito como lider de servidor federado fed-3, luego se detuvo fed-3, fed-2, fed-1,  pero al iniciar fed-1 es decir no hya mas servidoores federados activos el servidor fed-1 nunca cambia su estatus a lider aunque no esten activos mas servidores, puedes mejorar el algoritmo para que determine que al no existir otro servidor federado el que este activo cambie su rol de maner auotmatica a lider.


 -[x] En el formulario web del servidor federado en la lista Estado Raft, añade ademas del ip que se muestra el  "NodeID" para
que se pueda idenficar apropiadamente el servidor federado. 
- [] En el login del servidor federado cuando se escribe el user name y el password no se logran ver las letras

- [x ]Ejecutar tres servidores federados y detener el principal y observar si se asigna el segundo federado como principal.
     Documentar en federated.md como debe ser el proceso a nivel de servidor federado y nodos.





- [x] Cuando se intenta crear un indice en el lider envia el error 
Info
Creating index...

×
❌
Error
Failed to create index: Not Leader. Writes must be sent to the cluster leader.

Lo que el lider no sabe que es el lider, es decir, el servidor federado conoce que ese nodo es el lider pero el lider no sabe que fue asignado como lider




- [x] Cuando se intenta crear un backup envia el mensaje Success
Backup created. Downloading...

× Error
Download failed: Query parameter "token" is not available

Y no lo muestra en el listado de backup ni permite descargarlo 
- [x] Cuando se intenta descargar un backup envia el mensaje Download failed: Query parameter "token" is not available y no deja descargarlo


- [x] Cuando crea un backup en la interface web en la pagina Backup y restore salta a otra pagina y envia el mensaje Unauthorized
y no aparece en la lista de backup para descargar


- [x] Modificar el dialogo eliminar indice en la interface web para que cuando muestre el dialogo de confirmacion por ejemplo Delete index on 'name'?. use estilo flowbiew css en lugar del dialogo estandar javascript
la idea es que se vea mas elegante.

- [x] Verifica que en los nodos que no son lider no pueda insertar , actualizar o eliminar bases de datos, coleccion, documentos, indices, reglas.


- [x] Verificar que distribuya las bases de datos desde el nodo lider a los nodos y que estos sepan cual es el lider mediante consultas al servidor federado



- [x] Para mantener la seguridad una vez que se reinicia o detienen los nodos la interface web debe dirigirse al login en cuanto inicia para que el usuario se autentique esto debe ser aplicacado en el servidor federado y en el lider y en los nodos
para evitar que un usuario use la interface web sin autorizacion.

-[x] Cuando un nodo este inactivo no debe estar hablitado el boton detener nodo  y lñas estadistocas  de consumo de CPU, RAM, DISCO Y LATENCIA DEBEN ESTAR EN CERO en el formulario web del servidor federado




- [x] el dialogo se queda con el mnensaje Comando de detención enviado al nodo node-2
Entendido y no se cierra automaticamente, se puede colocar un progress bar indicando el proceso y en cuanto finalice de manera autom,atica cerrar ese dialogo

- [x] Realizar metricas de cada nodo (consumo de ram, espacio libre, consumo de cpu, latencia)
- [x] Cuando intentas detener un nodo puedes cambiar el dialogo que dice ¿Está seguro de que desea detener el nodo node-2? qiue es un dialogo estarndar javsactrip por uno mas elegante usando flowbitecss y has lo mismo para el dialogo Comando de detención enviado la idea es que los dialogos se vean mas elegantres
- [x] En Servidor Federado debe  permitir detener un nodo, removerlo, ver el estado de metrics(Memoria Ram, consumo de CPU, Disco duro disponible, latencia de red) y actualizar el nodo correspondiente con el cambio realizado se debe notificar al nodo.
     realizarla implementacion desde la interface web en el formulario Dashboard del Cluster




- [x] El reiniciar el servidor desde el formulario web Configuración del Nodo (config.json), lo detiene automaticamente

pero se queda detenido y no reinicia de manera automatica el nodo. (El reinicio debe ser manual)



- [x] Eliminar la opcion que tenia que mostraba todos los nodos. Esa opcion solo se vera desde el servidor federado.

- [x] Cuando el lider no esta disponible el sistema no asigna un nodo como nuevo lider

- [x] En el formulario web configuracion de jettra server no muestra el contenido del archivo config.json para que l  usario con rol admin pueda editarlo.

- [x] Cometiste un error eliminaste el formulariom de configuracion del proyecto jetrta server en la interface web

-[x]En el formulario Federated Cluster Management del proyecto jettra-server,  cambia todos el formulario web, y permite editar el archivo config.json y añade dos bortones uno Guardar sin Reiniciar, que guarda los cambios y no reinicar el servidor de bases de datos y el otro Guardar y Reiniciar que guarda los cambios y reinicar al servidor de la base de de datos

- [x] Eliminar el formulario Cluster del proyecto jettra-server y modifcar el formulario Federated Cluster Management.
De manera que muestre el archivo de configuaracion y permita modificar el archivo config.json 
- [x] Puedes añadir una interface grafica para el servidor federado qe contenga un login, y un dashboard que permita ver todos los nodos y sus estados indicando el nodo lider
- [x] Actualizar curl, shell, driver con el uso de servidor federado y actualizar la documentacion respectiva
- [x] Crear la interface web del servidor federado.

- [x] Crea un proyecto a nivel de jettra-server llamado jettra-federated cuya funcion es actuar como servidor federado
este servidor no almacena bases de datos ni reglas ni indices es un enrutador interno que se usa para asignar 
mediante algoritmo de consenso el lider y los nodos leyendo la configuracion de config.json
El actualiza la base de datos del lider con los nodos y el estatus y se asegura de que sean replicadaos a todos los nodos
si el lider falla asigna otro nodo como lider.
Si falla el servidor federado otro servidor federado toma el lugar de lider federado.
Actualiza la interfaces web, el shell, curl y driver para implementarlo.
Ademas crea en /books/guide/federated.md donde se describe el objetivo como se implementa.




# Distributed Databases

- [x] se mantiene el error que muesta en la parte grafica el nombre de nodo 1 para todos los nodos y debe ser el nombre de cada nodo, y en el nodo 3 marcha que el nodo 1 y nodo2 estan inactivos cuando no es cierto ademas mostrar la inforamcion del estado de cada nodo, como cpu consumidad, ram disponible, espacio en disco disponible

- [x] En los grafos simpre coloca como nombre en el circulo nodo 1 y a todos les pone comoe estado follower. 
Ademas agregar metrics a cada nodo, de manera que se pueda mionitoorraear ele stados, cuando cpc concume, cuanto disco consume y cuanta memoria ram tiene disponible


- [x] Ocurre una falla cada cierto tiempo convierte el lider en nodo aunque este funcionando bien y a los pocos segundos lo vuelve a convertir en lider
esto puede provocar inconsistencia ya que dejaria en ciertos momentos al cluster sin un lider aunque el lider nunca se ha detenido.

- [x] Hacer pruebas desde el shell

- [x] Hacer pruebas desde Curl

- [x] Hacer pruebas desde el driver

- [x] Crear un proyecto de ejemplo del driver en una carpetra al nivel de jettra-server llamado drivertest



# Raft Algoritmo

Nanoservicio gRPC (Comunicación Inter-Nodo Raft)
gRPC es perfecto para Raft porque usa HTTP/2, lo que permite RPCs bidireccionales y una estructura de servicio bien definida (via .proto).


- [x] Cuando un nodo deja de funcionar no actualiza el estado a inactivo en la interface Web

- [x] Actualizar la administracion de nodos desde Shell, Driver, Curl y actualizar la documentacion correspondiente

- [x] Mostar de manera grafica los nodos y el lider 

- [x] Añadir la posibilidad de detener el envio de datos a un nodo y poder restaurarlo

- [] Probar remover un nodo desde el lider

- [x] sigue el error no permite ingresar al sistema se queda en el formulario login

- [x] añade el nodo la coleccion pero esta mo se muestra los registros que estan en la coleccion _clusternodes en la interface grafica para permitir administrar desde el lider o observarlos desde el cliente.

- [x], hay nun error en la interface web no muestra en la lista Peers el nodo añadido, recuerda que cuanto añade un nodo debe ser almancenado en _clustersnodes

- [x] El proceso de escritura es un poco lento al usar programacion distribuida puedes mejorar el algoritmo.

- [x] En el formulario web Cluster no muestra los registros de la coleccion _clusternodes y se queda tratando de registrar un nuevo nodo.

- [x] porque en la interface web cuando selecciono una bae de datos en el menu izquierdo y veo una coleccion y sus docmentos, congela las otras opciones del menu , tengo actualiar la pagina en el navegador para poder usar las opciones

-[x] Debes corregir la situacion que si se incian los nodos y todos aparecen como lider, al añadirlo a un lider se convierte en nodo y deja de ser lider, ademas en el ainterace formulñario Cluster al añadirlo  no lo muestra en la lista de nodos y su estado.
- [x] En el formulario Cluster de la interface web no muestra los nodos agregados  estos se pueden almacenar en la base de datos _:system  y la coleccion _clusternodes que se debe actualizar y distribuir a todos los nodos. En este caso elimine el uso de federated.json y administre todo en la coleccion _clusternodes
     de la misma manera ajuste shell, curl, driver para que los nodos del cluster se administren con esta nueva coleccion y la coleccion es la que se distribuye a los nodos.

- [x] No permite añadir un nuevo nodo en el lider ni mostrar el estado de cada nodo
- [x] Recuerda el lider puede añadir, remover, detener nodos y estos se debe replicar a los nodos con el nuevo estado.


- [x] Al iniciar se verifica el estado de los nodos para ver cuales estan activos e inactivos.
- [x] Una vez que el nodo este activo se envia el archivo federated.json y se compara con el que tiene el nodo para actualizarlo
- [x] Debe mostrar el estado de cada nodo del cluster
- [x] Debe distribuir la  base de datos del lider a los nodos para que esten sincronizados incluyendo indices, rules y user.
- [x] Recuerda que solo el lider puede añadir nodos o remover, detener un nodo y notificar a todos los nodos mediante el archivo federated.json



- [x] Puedes usar una estrategia diferente con "peers": en lugar de usar el archivo config.json, use el archivo federated.json
Este archivo contendra los nodos y su rol (lider, candidato, nodo) el unico nodo que puede hacer modificaciones en el es el lider
y este archivo se replicada a cada nodo. Pero ellos no pueden hacer cambios en el archivo, y cuando se añade o remueve un nodo
este es administrado por el lider y distribuido a cada nodo del cluster, de manera que cuando el lider deje de funcionar
cualquier nodo toma el papel del lider y puede añadir o remover nodos del cluster y actualizar este archivo. 
Tenga presente que ningun nodo que no sea lider podra añadir o remover nodos.
Para añadir o remover nodos se hara desde la interface Web del lider o mediante Shell , curl, driver todos ellos desde el lider.
Y actualiza la documentacion en /books/guide/distributed.md

Por lo tanto el archivo federated.json se crea la primera vez en el lider.






ya que al añadir un nuevo nodo no sabria cuales son los demas. En este caso se debe actualizar con todos los nodos registrados.
Es decir al crear uno nuevo o eliminar un nodo, el archivo federated.json se actualiza automaticamente con los nodos que lo componen

. Cuando se ejecuta una instanncia nueva no es necesario 
registrar los peers, ya que la actualizacion se hace desde la interface grafica donde se registran los nodos.
Esto se hace de esta manera para evitar que un usuario cree un nodo y se conecte sin la autorizacion correspondiente.

- [x] Recuerda cuando se asigna un nuevo lider este debe tener en el archivo config.json   "Bootstrap": true, y los demas   "Bootstrap": false.

- [x] Recuerda distribuir el indice y las versiones de los documentos



- [x] Implementar la distribucion de datos (base de datos, colecciones y documentos a otros nodos estilo replicaset pero mediante el concepto
de bases de datos distribuida aplicando algoritmos Raft, ten presente que se pueden crear varios nodos desde codigo nativo es decir
ejecuando instancias de la base de datos en diversos servidores o mediante imagenes de contenedores de docker o podman 
o en entornos Kubernetes. Crea los archivos necesarios y crea la documentacion para implementar en cada caso
en el archivo /books/guide/distributed.md
Ademas permite que se configuren los nodos desde la interfaces web, y que tambien se configure cada archivo config.json para indicar los nodos
y el lider que se usara. Ten en cuaenta que si falla el lider otro nodo debe convertirse en lider.
- [x] El proceso seria primero crear un cluster que se almacena en _cluster y luego al ejecutar cada instancia el que tiene marcado en el archivo
config.json  "Bootstrap": true, sera usado como lider, y debe contarse con una opcion del menu en la interface web que indique Cluster
donde se muestre el nodo actual, se muestren todos los nodos asignados el cluster se muestre el nodo lider y permita añadir, remover, ver el estado 
de cada nodo. Ten presente que si el lider se detiene otro nodo debe tomar por consenso el rol de lider. Esto debe ser un proceso automatico
en el caso que solo existan dos nodos y uno se detiene usar un rol de consenso especial ya que no hay mas nodos el que este activo tomara el rol de lider

-[x] En el archivo config.json añadir una propiedad llamada distributed que contendra true para que se aplique en entornos distribuidos o false indicando 
que sera una base  de datos local sin distribuir datos.





# Testing

-[x] La interface Web no permite agregar una nueva base de datos se queda en el boton Create Database y no realiza ninguna operacion
-[x] La opcion Cluster no muestra ningun formulario para crear nuevos clusters.
-[x] La opcion indices  no muestra el formulario para agregar nuevos indices a las colecciones

- [x] El el formulario crear coleccion no se cierra el crear la colleccion desde la interface web y no envia el mesnsaje que fue guardado.



- [x] El shell no muestra la paginacion de resultados es decir debe mostrar 10 y luego indicar el menu para desplazarse, y añade la instruccion count para contar registros y añadelo a /books/guide/count.md
Por ejemplo cuando realizo una consulta con un select o find y contiene muchos registros se muestran todos y solo deberia mostrar unos 10 0 5 y mostrar un menu de desplazamiento 
para poder ir al inicio , final, siguiente o anterior. Es decir se usa paginacion.


- [x ] Usar compact Header de java 25 para optimizar el tamaño de los objetos almacenados con JettraStoreEngine y ejecuta el test nuevamente para verificar
si mejora el rendimiento y el tamaño ocupado.  java -XX:+UseCompactObjectHeaders. Ejecuta test con JettraEngineStore vs JettraBasic y determina cual es el mejor y
si puedes optimizar JettraEngineStore seria ideal.



-[x] corrige el error que en el formulario de la interface web Query Console, no muestra ninguna base de datos en la lista para seleccionar


-[x] Para ambas bases de datos ejecute test para medir cual formato es mas optimo para almacenamiento, serializar, deserializar y las operaciones crud.
Genera un reporte en /books/guide/result.md con el resultado del test mediante comandos curls.

- [x] El dialogo de eliminar una base de datos en la interface grafica debe usar un mejor dialogo y no el estandar javascript que no se ve muy elegante.

- [x] Verificar que se ejecuten y documenten las intrucciones como agregaciones, joins, count distinct entre otras.

-[x] En la interface web las opciones Indexes, Clusters, Rules, TRansacctions,, Query, create backup, import export, users entodos estos fromularios al seleccionar la lista de la base de datos no se muestra correctamente el nomnre de la base de datos se muestra [object Objetc] en lugar del nombre correcto de la base de datos
- [x]Existe la forma de optimizar JettraEngineStore comparado con JettraBasicStore y ejecuta nuevamente los test


- [x] Cree la base de datos llamada almacenbasicdb en la que continie las coleccciones siguientes
  * clientes
  * categoriasproductos
  * productos (recuerde que los productos estan asociados a una categoria)
  * facturas
  * facturasdetalles

Ingrese 50000 clientes, 100 categorias de productos 10000 productos. Y genere facturas para simular las ventas con un  total de 1 millon de facutras
cree el formulario para crear las facturas y sus detalles y que permita consultarlos.

Para este ejemplo use almacenamiento JettraBasic.

# Base de datos prueba Jettra Storage Engine

- [x] Cree la base de datos llamada almacenstoreenginedb en la que continie las coleccciones siguientes
  * clientes
  * categoriasproductos
  * productos (recuerde que los productos estan asociados a una categoria)
  * facturas
  * facturasdetalles

Ingrese 50000 clientes, 100 categorias de productos 10000 productos. Y genere facturas para simular las ventas con un  total de 1 millon de facutras
cree el formulario para crear las facturas y sus detalles y que permita consultarlos.

Para este ejemplo use almacenamiento Jettra Storage Enginec.




-[x] Crea en /books/guide un documento llamado storageengine.md que explique el concepto de los engine que se estan implementando
JettraSimpleStore y JettraStoreEngine, con sus objetivos ventajas desventajas y como se implemnta en curl, shell, driver e interface grafica.


- [x] Cuando se crea una base de datos y se usa Engine Store este no se almacena en la coleccion _engine de la base de datos. 
- [x] Añadir un icono de informacion  en las opciones de la base  de datos que indique informacion de la base  de datos y el engiene que usa es decir un dialogo que muestre estos datos.



- [x] en la interface web  cuando se inserta un nuevo documento con el formato Jetta engine Store. no se muestra en el formulario puedes corregirlo
Mostrar la informacion de la base de datos y al seleccionar el tipo de engine almacenarlo en _engine para que se pueda usar por otros elementos, Por favor actualiza la documentoacion de curl, shell, driver que se indique como especificar el tipo de engiinge·


- [x] Al crear la bae de datos seleccionar el engine se debe crear un documento men la coleccion _engine de la base de datos con la informacion del engine seleccionado. Cuando es un suuario que no tiene privilegio admin no mostrar las colecciones _engine ni _info. Ademas añadir una opcion que muestre el tipo de engine que usa la base de datos

- [x] Cada vez que se crea la base de datos se crea la coleccion _engine que indicara el motor de almacenamiento
     jettra usa JettraBasicStore y JettraEngineStore (optimizado para objetos Java lo que hace mas eficiente)

-[x] Crear un formato de serializacion Java optimizado para el almacenamiento y para la carga en memoria
que sea eficiente y pequeño  comparado con JSON, Apache Avro o ProtoBuf. Este formato debe permitir operaciones rapidas en Java
ya el engine esta creado en Java, y pasar del storage a los objetos debe ser muy eficiente. 
Modificar la base de datos para que permitea
almacenar en este formato y comprobar que es mas eficiente que el formato actual que se esta usando.
Para implementar JettraStorageEngine el usuario debe indicar al inicio si usara el metodo tradicional que actualmente tiene o
aplica Jettra Storage Engine para optimizacion.
Modificar el shell, Driver , curl y la interface web para que soporten este nuevo formato optimizado.

- [x] Cuando se crea una base de datos se debe indicar el motor storage a usar ya sea  JettraSimpleStore o Jettra Store Engine.
Por lo tanto desde la interface web, shell, driver o curl debe especificarse el motor de almacenamiento a usar y de la misma manera
cuando se va  interactuar con la base de datos y las colecciones se debe leer la informacion sobre el tipo de _engine que utiliza.

- [x] Recuerde que java 25 utiliza Compact Header para reducir el tamaño de los objetos esto puede ser util para JettraStoreEngine
porque las operaciones Java son mas eficientes al ser objetos Java almancenados y de tamaño reducido. 

- [x] Por favor crea

# Versiones
- [x] Incluir soporte para versiones de documentos de manera que los documentos eliminados o cambiados se almacenen como versiones
que pueden ser recuperadas desde una interface web , curl, shell o driver. Estas versiones no deben interferir en las operaciones
normales que se realicen sobre la coleccion. Para su almacenamiento se usa un algoritmo de tipo LSM ya que solo se iran guardando y la ultima
debe quedar como primera opcion para recuperacion y asi susecivamente.

- [x] En la inteface web por favor muetra el contenido de cada version del documento lo que permitira decidir la restauracion 
- [] Incluir el soporte para ver las versiones  y restaurar una version desde shell, curl, driver java y crear la documentacion de uso.


# Shell
- [x] Añadir la posibilidad de que se recorra el historial de comandos ejecutados en el shell y la opcion de autocompletado

 
# Generacion

Permita la creacion de imagenes para 

- [x] Docker

- [x] Postman

- [x] Imagen Nativa

- [x] Criu/Crac



# Shell

- [x] Añade la opcion de crear nuevos usuarios y su rol para cada base  de datos.
- [x] Implementar paginacion en los resultados es decir devolver 10 e implementar el desplazamiento usando (f) Primero
(n) para siguiente (b) para anterior y (l) para el ultimo y mostrar el menu.
- [x] Crea la documentación en /books/guide/shell.md



# DataFrame EC

- [x] Implmentar el soporte para la libreria Dataframe EC java https://github.com/vmzakharov/dataframe-ec y crear la documentacion en /books/guide/dataframeec.md
la implementacion debe ser soportada por el driver, shell, curl y la interface web.

# Eclipse Collections
- [x] Implementar el Soporte para Eclipse Collections crear la documentacion en /books/guide/eclipsecollections.md
la implementacion debe ser soportada por el driver, shell, curl y la interface web.



 # Database
 - [x] El sistema debe contar con una base de datos llamada _system y una coleccion llamada _users que almacena los usuarios
        y roles dentro del sistema cada vez que sea crea una base de datos se generan.



# Exportar
- [x] Crear un formulario en la interface web llamado exportar y alli se selecciona la base de datos de una lista y selecciona la coleccion de una lista y se selecciona el formato
a exportar csv, json.
- [x] Añadir el soporte de exportacion desde curl, shell y driver documentar en /guide/exportar.md
- [x] añadir la opcion de exportacion a formato csv o Json  desde interface web, shell. curl y dirvaer y crear el archovo /guide/export.md (en la intrerface web se crea un formulario que selecciona la base de datos y la coleccion y el tipo de exportacion  y al dar clic en export se generar el archivop de exportacion,.
- [x] añadir la opcion de importar unj archivo en formato csv o json, en un formulario web se elecciona el arcvhivo y la base de datos y la coleccion a importarlo. Añadir soporte para shell, curl. y diuver y crear la documentaicon en /guide/im port.md
- [x] aÑAIR A LA INTERFACE WEB EL formulario de restauracion que no se visualizar



# Seguridad

- [x] puedes añadir seguridad a la base de datos mediante user, password y rol. y crear para cada base dde datos una coleccion llamada _system donde se almancen los usuarios y roles. El rol inicial es user: admin, password: adminadmin, role:admin que tiene privilegfios de superusuario, y modifica el codigo para que se valide las credenciales de los usuarios antes de realizar cualquier operacion. Los roles validos son admin (que es el super usuario),  reader(solo lectura) , writereader(lectura escritura), owner(propietario de la base de datos).


# Rules (Integridad Referencial)
- [ Cuando se intenta eliminar un documento de la llave primaria y tiene registros en la coleccion 
     secundaria que dependen de el no se debe permitir la eliminacion..
     Ejemplo no se debe permitir eliminar un documento de la coleccion pais que sea usado como referencia en la colección persona.
- [x] Añadir una regla de no permitir valores nulos
- [x] Añadir la regla de no permitir valores vacios
- [x] Añadir la regla de no permitir numeros negativos
- [x] Añadir la regla de no permitir valores menores de un valor indicado
- [x] Añadir la regla de no permitir valores mayores de un valor indicado
- [x] Añadir la regla de no permitir valores que no esten en un rango



# Structed
- [] Permitir definir tipos de datos que seran aplicados a los datos de documentos de una coleccion
por ejemplo: base de datos: ventasdb. Se debe generar una coleccion nueva para cada base de datos al 
momento de crearla adicional a la coleccion _rules, esta nueva coleccion se llama _definition y permite
definir los tipos de datos por ejemplo
{
   "colecction": "ventas",
   "field":[
            { "name":"codigo",
              "type":"string"  
            },
            {
              "name":"precio",
              "type":"double"
            },
            {
             "name":"fecha",
             "type":"date"
            }
           ]
}

Esta definicion de estructura se usara para almacenar o recuperar informacion.





# Transacciones
-[x] Crea un formulario en la interface web para probar las transaccionesn el usuario selecciona de una lista la base de datosy puede insertar varias operaciones indicando roolback o commit.

-[x] Añadir soporte para transacciones con operaciones como rolback y commit tener presente que se va
a implementar con Replicaset y Sharding entre varios nodos
-[x] Documentar las transacciones




# Backup

- [x] Crear un shell llamado jettraBackup que ejecuta un backup de la base de datos a un archivo .zip
- [x] Exportar una coleccion a JSON
- [x] Exportar una coleccion a CVS

# Restore
- [x] Crear un shell llamado  JettraRestore que realiza restauracion de un backup que se creo con JettraBackup




# Driver Java

- [x] Implementacion basica del driver
- [x] El driver debe permitir Convertirlo a Java records y viceversa y añade a la documentacion ejemplos de uso
- [x] Ejemplo con una interface repository con agregaciones



# Shell

- [x] Se creo un documento llamado /guide/JettraShell.md que tiene la sintaxis basica por favor incluye el soporte para todas las operaciones crud  de bases datos, operaciones cruid de coleccion y operaciones crurd a nivel de documentos, incluue crud para indices y busquedas por indices, ademas incluye agrupaciones, aggregaciones para operaciones mas complejas, referencias entre documentos e esto usando el lemguaje propio JettraQueryLanguaje, tambien incluye spporte para SQL y un lenguaje similar a MongoDB. Estos deben ser documentados con ejemplos de todo tipo para que sirvan de referencia al usuario.




# Start

- [] Cuando incia la aplicacion verificar si existe el archivo config.json si no existe crearlo con una estructura similar
a la siguinte 
{
  "Host": "0.0.0.0",
  "Port": 6585,
  "DataDir": "./data",
  "Role": "Obsolete",
  "SessionTimeout": 0,
  "Bootstrap": true,
  "ClusterID": "cluster-1",
  "NodeID": "node-1"
}

- [] Verifique si no existe el directorio data y si no existe lo crea, ademas crear la base de datos _system y la coleccion _user donde se almacena los usuarios y sus credenciales. Recuerde que debe 
crear un usuario con rol de administrador (username: admin,password:adminadmin, role:admin). Ademas cree la coleccion
_cluster que almacenara la informacion de los cluser(clusterID, cluster, descripcion). Estas se deben crear cuando se ejecuta
la aplicacion y no existen.


esto brindara informacion sobre el ip y puerto de ejecucion ademas del cluster y nombre de nodo.




# Interface Web
- [x] Añade el formulario para crear usuarios para administrar base de datos y el rol permitido para cada base de datos.

- [x] En el top superior de la plantilla mostrar la opcion para cambiar de tema dark a white,

- [x] Crear un dashboard inicial que muestre estadisticas

- [x] Crear un formulario para cambiar la contraseña

- [x] Añade un formulario para cambiar la contraseña del usuario logeado, y crea un formulario que muestra las bases de datos en un arbol y permita crear nuevas bases de datos, editar y eliminar. Y al seleccionar la base de datos se muestra una lista de las colecciones que tiene y debe permitir crear nueva coleccion, editar el nombre de una coleccion existente o eliminar la coleccion. Y al seleccionar la coleccion debe mostrar una seccion donde se pemita crear un nuevo documento de esa coleccion y al guardoarlo lo muestre en una lista de documentos que pueden tener la visualizacion de tipo JSON, Tabla o Arbol. y Cada documento debe permnitr que se edite , elimine realice busquedas, y al dar click en cualquier documento este se marque como seleccionado.

- [x] Mostrar el usuario seleccionado

- [x] Crear la interface WEb responsiva con menus hamburguesa menu izquierdo con las opciones y en panel principal el dashboard inicial
    utilice Flowbie Css para el diseño responsivo, debe contar un formulario de login que solicite las credenciales del usuario.





