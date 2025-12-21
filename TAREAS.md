# TASK


java -XX:+UseCompactObjectHeaders -jar jettraFederated.jar

java -XX:+UseCompactObjectHeaders -jar jettraDBVM.jar

java -XX:+UseCompactObjectHeaders -jar jettraDBVMShell.jar

# Benchmarking

./run_benchmark_comparison.sh

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


# jAVA 25

- [ ] Usar Bellsoft Native Image

[BellSoft](https://bell-sw.com/pages/downloads/#jdk-25-lts)

# JettraMemoryDB
- [] Crear la base de datos JettraMemoryDB que es una base de datos Key-Value en memoria desarrollada en Golang y que contiene un driver Java
     esta base de datos se usa con el servidor federado para almacenar los datos en memoria , mientras un proceso asincrono 
      se encarga de persistir los datos en el lider y los nodos controlados por el servidor federado.


- [] Crear la base de datos totalmente en memoria y que usa JettraDBVM como motor de almacenamiento


# Servidor Federado
- [] Realizar metricas de cada nodo (consumo de ram, espacio libre, consumo de cpu, latencia)

- [] Actualiza el shell, driver, curl, para que se conecten al servidor federado en lugar de la base de datos directamente y documenta esta opracion en federated.md 
y actualiza los archivos .md que se refieren al tema.

- [] Que ocurre si no existe un servidor federado para gestionar el lider y los nodos, ya que las operaciones 
deben pasar por el servidor federado.



- [] Verificar que distribuya las bases de datos desde el nodo lider.
- [] Verifica que en los nodos que no son lider no pueda insertar , actualizar o eliminar bases de datos, coleccion, documentos, indices, reglas.


- [] Puedes documentar como acceder a la interface web del servidor federeado y cambia la contraseña inicial por adminadmin y crea un registro que permita cambiar la contraseña y almacenarla para mayor seguridad. y en el Dashboard Federated siempre dice que esta activo el nodeo cuando no esta activo.
- [] En la interface grafica de cada nodo solo debe mostrar al servidor federado al que pertenece


- [ ]Ejecutar tres servidores federados y detener el principal y observar si se asigna el segundo federado como principal.


- [] En Servidor Federado debe  permitir detener un nodo, removerlo, ver el estado de metrics y actualizar el nodo correspondiente con el cambio realizado.


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







# Referencias entre documentos


# Jettra Storage 



# Base de datos prueba JetrraBasic





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
- [x] En el formulario Cluster de la interface web no muestra los nodos agregados  estos se pueden almacenar en la base de datos _:system  y la coleccion _clusternodes que se debe actualizar y distribuir a todos los nodos. En este caso elimine el uso de cluster.json y administre todo en la coleccion _clusternodes
     de la misma manera ajuste shell, curl, driver para que los nodos del cluster se administren con esta nueva coleccion y la coleccion es la que se distribuye a los nodos.

- [x] No permite añadir un nuevo nodo en el lider ni mostrar el estado de cada nodo
- [x] Recuerda el lider puede añadir, remover, detener nodos y estos se debe replicar a los nodos con el nuevo estado.


- [x] Al iniciar se verifica el estado de los nodos para ver cuales estan activos e inactivos.
- [x] Una vez que el nodo este activo se envia el archivo cluster.json y se compara con el que tiene el nodo para actualizarlo
- [x] Debe mostrar el estado de cada nodo del cluster
- [x] Debe distribuir la  base de datos del lider a los nodos para que esten sincronizados incluyendo indices, rules y user.
- [x] Recuerda que solo el lider puede añadir nodos o remover, detener un nodo y notificar a todos los nodos mediante el archivo cluster.json



- [x] Puedes usar una estrategia diferente con "peers": en lugar de usar el archivo config.json, use el archivo cluster.json
Este archivo contendra los nodos y su rol (lider, candidato, nodo) el unico nodo que puede hacer modificaciones en el es el lider
y este archivo se replicada a cada nodo. Pero ellos no pueden hacer cambios en el archivo, y cuando se añade o remueve un nodo
este es administrado por el lider y distribuido a cada nodo del cluster, de manera que cuando el lider deje de funcionar
cualquier nodo toma el papel del lider y puede añadir o remover nodos del cluster y actualizar este archivo. 
Tenga presente que ningun nodo que no sea lider podra añadir o remover nodos.
Para añadir o remover nodos se hara desde la interface Web del lider o mediante Shell , curl, driver todos ellos desde el lider.
Y actualiza la documentacion en /books/guide/distributed.md

Por lo tanto el archivo cluster.json se crea la primera vez en el lider.






ya que al añadir un nuevo nodo no sabria cuales son los demas. En este caso se debe actualizar con todos los nodos registrados.
Es decir al crear uno nuevo o eliminar un nodo, el archivo cluster.json se actualiza automaticamente con los nodos que lo componen

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





