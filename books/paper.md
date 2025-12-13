# Paper

# Seguridad mediante Certificados nodo-lider-nodo

Cada nodo que se conecta, envia su configuracion mediante un certificado digital al lider, este firma
el certificado y es devuelto al nodo. Cada operacion del nodo verifica si el certificado no ha sido
alterado y si no ha expirado para evitar ataques.

Cuando un nuevo lider es asignado todos los nodos envian su certificado al nuevo lider que procede 
a firmarlos para interactuar con ellos.


