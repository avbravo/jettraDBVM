Servidor Federado Implmentando ajustes para agregar nuevos servidores

Se cuentan con tres servidores federados puede ver la configuracion del archivo federated.json
el primero  (fed-1)
{
    "Mode": "federated",
    "Port": 9000,
    "NodeID": "fed-1",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002"
  ]
}

el segundo (fed-2)
{
    "Mode": "federated",
    "Port": 9001,
    "NodeID": "fed-2",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002"
  ]
}

el tercero(fed-3)
{
    "Mode": "federated",
    "Port": 9002,
    "NodeID": "fed-3",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002"
  ]
}

puede observar que todos los servidores estan activos y poseen la misma configuracion en  "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002"
  ]
  
 
 Cuando se va a crear un nuevo servidor llamado fed-4 se cuenta con esta informacion
 (fed-4)
 {
    "Mode": "federated",
    "Port": 9004,
    "NodeID": "fed-4",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9004"
  ]
}

puede observar que el la configuracion  
"FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9004"
    
 Contiene al menos uno de los servidores federados en este caso "http://localhost:9000", que corresponde a fed-1
 y contiene su propio  "http://localhost:9004" pero ningun otro servidor fed-1, fed-2, fed-3 lo contienen ya que es un nuevo servidor.
 La forma de proceder en estos casos con nuevos servidores federados seria la siguiente.
 
1. Se ejecuta el nuevo servidor federado fed-4, este se conecta por almenos uno de los servidores federados a la red. (   "http://localhost:9000")
2. Se identifica cual es el servidor federado lider de la red en este ejemplo es fed-1.
3. El lider obtiene el url del nuevo servidor federado fed-4 ("http://localhost:9004")
4. El lider lo compara con su archivo federated.json
{
    "Mode": "federated",
    "Port": 9000,
    "NodeID": "fed-1",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002"
  ]
}

al no estar incluido en "FederatedServers":, lo añade localmente quedando

{
    "Mode": "federated",
    "Port": 9000,
    "NodeID": "fed-1",
     "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002",
     "http://localhost:9003"
  ]
}

Ahora esta configuracion es enviada a todos los nodos de la red federada (fed-2, fed-3) e incluso el nuevo fed-4.
 "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002",
     "http://localhost:9003"
  ]
  
 Cada nodo toma en local esta configuracion y actualiza el archivo federated.json de manera que todos los nodos y el lider contengan la misma informacion
  "FederatedServers": [
    "http://localhost:9000",
    "http://localhost:9001",
    "http://localhost:9002",
     "http://localhost:9003"
  ]
  
  Una vez distribuido los nodos realizan un proceso de Hot-Reloaded, es decir se reinician en caliente para obtener la configuracion del nuevo servidor.
  
  El lider se mantiene como lider hasta que ocurra un evento que cambie la situacion.
  
  # Eliminacion del un servidor Federado
  
  Este mecanismo de propagacion ocurre igual cuando se elimina un servidor federado el lider lo elimina de su configuracion en federated.json y distribuye a todos los nodos federados y estos hacen el hot-Reloaded
  
  # Arranque de un servidor Federado
  
  Para mantener la sincronizacion en todo momento y evitar que un usuario elimine de su configuracion local federated.json o añada uno nuevo sin la autorizacion cuando inicia cada servidor federado
envia la informacion de  "FederatedServers": al lider este verifica con la configuracion local y si no se trata de un nuevo servidor federado ni de uno que fue removido, el servidor federado envia la configuracion al servidor federado este actualiza en local el archivo federated.json, y ocurre el Hot-Realoded.
Esto permite mantener la coherencia de la red de servidores federados.


Esa logica funciona perfecta para los servidores federados ahora se necesita aplicar la misma logica para los nodos de bases de datos

cuando un nodo de base de datos se conecta a la red federada. El nodo de base de datos envia al servidor federado su configuracionç
que tiene en el archivo config.json en especial "FederatedServers": , el servidor federado lo compara con "FederatedServers": del archivo
federated.json y devuelve al nodo la lista actualizad de "FederatedServers": , el nodo de base de datos
actualiza su archivo config.json local con el listado actualizado y hace Hot-Reloaded automatico para garantizar que todos 
los nodos tengan actualizado la lista de servidores federados.
Si el listado del nodo de base de datos es identico al del servidor federado no es necesario actualizar el archivo config.json del nodo de base de datos.



debes implementar el hot-reloaded a nivel del nodo de la base de datos ya que lo deja detenido y no vuelve a ejecutarlo. Recuerda que este se puede ejcutar como un java -jar, o mediante run.sh o mediante imagene nativa o mediante docker o kubernetes por lo que debes tener en cuanta como reiniciar para cada uno. Y recuerda siempre garantizar que se actualice "FederatedServers" con el que posee el lider de los servidores federados



añadira la opciond e hot-reloaded desde el mismo codigo java es decir que las aplicaciones java soporten reiniciar y arrancar directamente esto se necesita para que los nodos y servidores federados en ciertas condiciones deben reiniciarse, y para no depender de aplicaciones externas para el reinicio sea una funcion dentro de los mismos proyectos

