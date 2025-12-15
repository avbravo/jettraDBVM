# TASK



# Versionamiento
- [] Crear un sistema de versionamiento que permitira la recuperacion de datos eliminados y cambiados y de auditoria.

# Versiones
- [] Incluir soporte para versiones de documentos de manera que los documentos eliminados o cambiados se almacenen como versiones
que pueden ser recuperadas desde una interface web , curl, shell o driver. Estas versiones no deben interferir en las operaciones
normales que se realicen sobre la coleccion. Para su almacenamiento se usa un algoritmo de tipo LSM ya que solo se iran guardando y la ultima
debe quedar como primera opcion para recuperacion y asi susecivamente.



curl -u admin:adminadmin "http://localhost:8080/..."

# Jettra Storage Engine

-[] Crear un formato de serializacion Java optimizado para el almacenamiento y para la carga en memoria
que sea eficiente y pequeño  comparado con JSON, Apache Avro o ProtoBuf. Este formato debe permitir operaciones rapidas en Java
ya el engine esta creado en Java, y pasar del storage a los objetos debe ser muy eficiente. Modificar la base de datos para que permitea
almacenar en este formato y comprobar que es mas eficiente que el formato actual que se esta usando.
Para implementar JettraStorageEngine el usuario debe indicar al inicio si usara el metodo tradicional que actualmente tiene o
aplica Jettra Storage Engine para optimizacion.
Modificar el shell, Driver , curl y la interface web para que soporten este nuevo formato optimizado.

- [] Cuando se crea una base de datos se debe indicar el motor storage a usar ya sea  Basic Compression o Jettra Store Engine


# jAVA 25
- [ ] Usar compact Header
- [ ] Usar Bellsoft Native Image



# Distributed Databases


- [] Implementar la distribucion de datos (base de datos, colecciones y documentos a otros nodos estilo replicaset pero mediante el concepto
de bases de datos distribuida aplicando algoritmos Raft, ten presente que se pueden crear varios nodos desde codigo nativo es decir
ejecuando instancias de la base de datos en diversos servidores o mediante imagenes de contenedores de docker o podman 
o en entornos Kubernetes. Crea los archivos necesarios y crea la documentacion para implementar en cada caso
en el archivo /books/guide/distributed.md
Ademas permite que se configuren los nodos desde la interfaces web, y que tambien se configure cada archivo config.json para indicar los nodos
y el lider que se usara. Ten en cuaenta que si falla el lider otro nodo debe convertirse en lider.
- [] El proceso seria primero crear un cluster que se almacena en _cluster y luego al ejecutar cada instancia el que tiene marcado en el archivo
config.json  "Bootstrap": true, sera usado como lider, y debe contarse con una opcion del menu en la interface web que indique Cluster
donde se muestre el nodo actual, se muestren todos los nodos asignados el cluster se muestre el nodo lider y permita añadir, remover, ver el estado 
de cada nodo. Ten presente que si el lider se detiene otro nodo debe tomar por consenso el rol de lider. Esto debe ser un proceso automatico
en el caso que solo existan dos nodos y uno se detiene usar un rol de consenso especial ya que no hay mas nodos el que este activo tomara el rol de lider

# Raft Algoritmo

Nanoservicio gRPC (Comunicación Inter-Nodo Raft)
gRPC es perfecto para Raft porque usa HTTP/2, lo que permite RPCs bidireccionales y una estructura de servicio bien definida (via .proto).


-[x] La interface Web no permite agregar una nueva base de datos se queda en el boton Create Database y no realiza ninguna operacion
-[x] La opcion Cluster no muestra ningun formulario para crear nuevos clusters.
-[x] La opcion indices  no muestra el formulario para agregar nuevos indices a las colecciones


- [x] El el formulario crear coleccion no se cierra el crear la colleccion desde la interface web y no envia el mesnsaje que fue guardado.





# JettraMemory
- [] Crear la base de datos totalmente en memoria y que usa JettraDBVM como motor de almacenamiento

# Referencias entre documentos

# Generacion

- [] UberJAr

- [] Docker

- [] Postman

- [] Imagen Nativa

- [] Criu/Crac




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





