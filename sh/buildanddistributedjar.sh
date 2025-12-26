#!/bin/bash

cd "$(dirname "$0")/.." || exit
echo "generando "

mvn clean package -DskipTests

echo ":::::::::::::::::::::::::::::::::::::::::::::::"




echo "Copiando servidores federados y base de datos..."

# Función para copiar JAR y el script de ejecución robusto
deploy_node() {
    DEST=$1
    JAR_SOURCE=$2
    JAR_NAME=$3
    
    echo "Deploying to $DEST..."
    mkdir -p "$DEST"
    rm -f "$DEST/$JAR_NAME"
    cp "$JAR_SOURCE" "$DEST/$JAR_NAME"
    
    # Copiar el script run.sh y ajustar permisos
    cp sh/run.sh "$DEST/run.sh"
    chmod +x "$DEST/run.sh"
}

# Servidores Federados
deploy_node "/home/avbravo/jettradbvmnodes/federated1" "jettra-federated/target/jettraFederated.jar" "jettraFederated.jar"
deploy_node "/home/avbravo/jettradbvmnodes/federated2" "jettra-federated/target/jettraFederated.jar" "jettraFederated.jar"
deploy_node "/home/avbravo/jettradbvmnodes/federated3" "jettra-federated/target/jettraFederated.jar" "jettraFederated.jar"
deploy_node "/home/avbravo/jettradbvmnodes/federated4" "jettra-federated/target/jettraFederated.jar" "jettraFederated.jar"
deploy_node "/home/avbravo/jettradbvmnodes/federated5" "jettra-federated/target/jettraFederated.jar" "jettraFederated.jar"

# Nodos de Base de Datos
deploy_node "/home/avbravo/jettradbvmnodes/node1" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node2" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node3" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node4" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node5" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node6" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"
deploy_node "/home/avbravo/jettradbvmnodes/node7" "jettra-server/target/jettraDBVM.jar" "jettraDBVM.jar"

echo "Generating shell..."
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node1/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node2/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node3/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node4/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node5/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node6/
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node7/


echo "____________________________________________________________"
# 2. Comprobamos si el directorio existe

DIRECTORIO=/home/avbravo/jettradbvmnodes/jettramemory/

if [ -d "$DIRECTORIO" ]; then
   echo "El directorio '$DIRECTORIO' ya existe."
else
    # 3. Si no existe, lo creamos
    echo "El directorio '$DIRECTORIO' no existe. Creándolo..."
    mkdir -p "$DIRECTORIO"
    
    # Comprobamos si la creación fue exitosa
    if [ $? -eq 0 ]; then
        echo "Directorio '$DIRECTORIO' creado con éxito."
    else
        echo "Error: No se pudo crear el directorio."
    fi
fi

echo "____________________________________________________________"

echo "Jettra-Memory"
cp jettra-memory/target/jettraMemory.jar /home/avbravo/jettradbvmnodes/jettramemory1/
cp jettra-memory/target/jettraMemory.jar /home/avbravo/jettradbvmnodes/jettramemory2/
cp jettra-memory/target/jettraMemory.jar /home/avbravo/jettradbvmnodes/jettramemory3/
cp jettra-memory/target/jettraMemory.jar /home/avbravo/jettradbvmnodes/jettramemory4/


echo "Jettra-MemoryShell"
cp jettra-memory-shell/target/jettraMemoryShell.jar /home/avbravo/jettradbvmnodes/jettramemory1/
cp jettra-memory-shell/target/jettraMemoryShell.jar /home/avbravo/jettradbvmnodes/jettramemory2/
cp jettra-memory-shell/target/jettraMemoryShell.jar /home/avbravo/jettradbvmnodes/jettramemory3/
cp jettra-memory-shell/target/jettraMemoryShell.jar /home/avbravo/jettradbvmnodes/jettramemory4/


echo "Proceso finalizado"