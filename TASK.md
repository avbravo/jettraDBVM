# TASK

- [] puedes añadir seguridad a la base de datos mediante user, password y rol. y crear para cada base dde datos una coleccion llamada _system donde se almancen los usuarios y roles. El rol inicial es user: admin, password: adminadmin, role:admin que tiene privilegfios de superusuario, y modifica el codigo para que se valide las credenciales de los usuarios antes de realizar cualquier operacion. Los roles validos son admin (que es el super usuario),  reader(solo lectura) , writereader(lectura escritura), owner(propietario de la base de datos).
- [ ] Option 2

curl -u admin:adminadmin "http://localhost:8080/..."

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
