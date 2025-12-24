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

echo "Proceso finalizado"