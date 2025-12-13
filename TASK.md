# TASK

- [] puedes añadir seguridad a la base de datos mediante user, password y rol. y crear para cada base dde datos una coleccion llamada _system donde se almancen los usuarios y roles. El rol inicial es user: admin, password: adminadmin, role:admin que tiene privilegfios de superusuario, y modifica el codigo para que se valide las credenciales de los usuarios antes de realizar cualquier operacion. Los roles validos son admin (que es el super usuario),  reader(solo lectura) , writereader(lectura escritura), owner(propietario de la base de datos).
- [ ] Option 2

curl -u admin:adminadmin "http://localhost:8080/..."


# jAVA 25
- [] Usar compact Header



# Referencias entre documentos

# Driver Java

- [x] Implementacion basica del driver
- [] El driver debe permitir Convertirlo a Java records y viceversa



# Shell

- [x] Se creo un documento llamado /guide/JettraShell.md que tiene la sintaxis basica por favor incluye el soporte para todas las operaciones crud  de bases datos, operaciones cruid de coleccion y operaciones crurd a nivel de documentos, incluue crud para indices y busquedas por indices, ademas incluye agrupaciones, aggregaciones para operaciones mas complejas, referencias entre documentos e esto usando el lemguaje propio JettraQueryLanguaje, tambien incluye spporte para SQL y un lenguaje similar a MongoDB. Estos deben ser documentados con ejemplos de todo tipo para que sirvan de referencia al usuario.

# Distributed Databases

-[] La interface Web no permite agregar una nueva base de datos se queda en el boton Create Database y no realiza ninguna operacion
-[] La opcion Cluster no muestra ningun formulario para crear nuevos clusters.
-[] La opcion indices  no muestra el formulario para agregar nuevos indices a las colecciones




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

# Generacion

- [] UberJAr

- [] Docker

- [] Postman

- [] Imagen Nativa

- [] Criu/Crac

    # Database
    - [] El sistema debe contar con una base de datos llamada _system y una coleccion llamada _users que almacena los usuarios
        y roles dentro del sistema.

    - [] Añadir usuarios desde interface WEb, Curl o Shell


# Interface Web

necesito que mejores el diseño usando un template que separe los elementos
-top
-footer
-left
-center
todos los elementos de opciones se cargaran en la seccion center de manera que se reutilice la plantilla en todos los formularios, recuerda hacerla responsiva

- [] En el top superior de la plantilla mostrar la opcion para cambiar de tema dark a white,

- [] Crear un dashboard inicial que muestre estadisticas

- [] Crear un formulario para cambiar la contraseña

- [] Añade un formulario para cambiar la contraseña del usuario logeado, y crea un formulario que muestra las bases de datos en un arbol y permita crear nuevas bases de datos, editar y eliminar. Y al seleccionar la base de datos se muestra una lista de las colecciones que tiene y debe permitir crear nueva coleccion, editar el nombre de una coleccion existente o eliminar la coleccion. Y al seleccionar la coleccion debe mostrar una seccion donde se pemita crear un nuevo documento de esa coleccion y al guardoarlo lo muestre en una lista de documentos que pueden tener la visualizacion de tipo JSON, Tabla o Arbol. y Cada documento debe permnitr que se edite , elimine realice busquedas, y al dar click en cualquier documento este se marque como seleccionado.

- [x] Mostrar el usuario seleccionado

- [x] Crear la interface WEb responsiva con menus hamburguesa menu izquierdo con las opciones y en panel principal el dashboard inicial
    utilice Flowbie Css para el diseño responsivo, debe contar un formulario de login que solicite las credenciales del usuario.

# Shell


# Backup/Restore
- [] Incluir opciones para realizar un respaldo de la base de datos a un archivo con la extension .zip el nombre 
del backup generado sera nombrebasedatos-yyyymmddhhmmss.zip (donde nombrebase de datos se reemplazar por el nombre 
de la base de datos de la que se desea hacer respaldo y los demas corresponden a la fecha y hora actual.
Ejecutarlo mediante curl, interface web, shell

- [] Incluir opciones para realizar una restauracion de la base de datos obtenido del archivo .zip generado en el backup
Ejecutarlo mediante curl, interface web, shell

- []

# Raft Algoritmo

Nanoservicio gRPC (Comunicación Inter-Nodo Raft)
gRPC es perfecto para Raft porque usa HTTP/2, lo que permite RPCs bidireccionales y una estructura de servicio bien definida (via .proto).
