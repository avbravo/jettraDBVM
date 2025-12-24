cd "$(dirname "$0")/.." || exit
echo "generando "

mvn clean package -DskipTests

echo ":::::::::::::::::::::::::::::::::::::::::::::::"

echo "Copiando servidor federado 1"
echo "Path /home/avbravo/jettradbvmnodes/federated1/"
rm -f /home/avbravo/jettradbvmnodes/federated1/jettraFederated.jar
cp jettra-federated/target/jettraFederated.jar /home/avbravo/jettradbvmnodes/federated1/jettraFederated.jar

echo "Copiando servidor federado 2"
echo "Path /home/avbravo/jettradbvmnodes/federated2/"
rm -f /home/avbravo/jettradbvmnodes/federated2/jettraFederated.jar
cp jettra-federated/target/jettraFederated.jar /home/avbravo/jettradbvmnodes/federated2/jettraFederated.jar

echo "Copiando servidor federado 3"
echo "Path /home/avbravo/jettradbvmnodes/federated3/"
rm -f /home/avbravo/jettradbvmnodes/federated3/jettraFederated.jar
cp jettra-federated/target/jettraFederated.jar /home/avbravo/jettradbvmnodes/federated3/jettraFederated.jar
echo ":::::::::::::::::::::::::::::::::::::::::::::::"


echo "Copiando shell de servidor federado 1"
echo "Path /home/avbravo/jettradbvmnodes/federated1/"
rm -f /home/avbravo/jettradbvmnodes/federated1/jettraFederatedShell.jar
cp jettra-federated-shell/target/jettraFederatedShell.jar /home/avbravo/jettradbvmnodes/federated1/jettraFederatedShell.jar

echo "Copiando shell servidor federado 2"
echo "Path /home/avbravo/jettradbvmnodes/federated2/"
rm -f /home/avbravo/jettradbvmnodes/federated2/jettraFederatedShell.jar
cp jettra-federated-shell/target/jettraFederatedShell.jar /home/avbravo/jettradbvmnodes/federated2/jettraFederatedShell.jar

echo "Copiando shell de servidor federado 3"
echo "Path /home/avbravo/jettradbvmnodes/federated3/"
rm -f /home/avbravo/jettradbvmnodes/federated3/jettraFederatedShell.jar
cp jettra-federated-shell/target/jettraFederatedShell.jar /home/avbravo/jettradbvmnodes/federated3/jettraFederatedShell.jar











echo "Copiando jettraDBVM.jar a /node1"
rm -f /home/avbravo/jettradbvmnodes/node1/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node1/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node2"
rm -f /home/avbravo/jettradbvmnodes/node2/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node2/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node3"
rm -f /home/avbravo/jettradbvmnodes/node3/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node3/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node4"
rm -f /home/avbravo/jettradbvmnodes/node4/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node4/jettraDBVM.jar




echo "Generating shell..."
cp jettra-shell/target/jettraDBVMShell.jar .
cp jettra-federated-shell/target/jettraFederatedShell.jar .


echo "Copiando JettraDBVMShell a /jettradbvmnodes/node1/"
rm -f /home/avbravo/jettradbvmnodes/node1/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node1/jettraDBVMShell.jar


echo "Copiando JettraDBVMShell a /jettradbvmnodes/node2/"
rm -f /home/avbravo/jettradbvmnodes/node2/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node2/jettraDBVMShell.jar

echo "Copiando JettraDBVMShell a /jettradbvmnodes/node3/"
rm -f /home/avbravo/jettradbvmnodes/node3/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node3/jettraDBVMShell.jar

echo "Copiando JettraDBVMShell a /jettradbvmnodes/node4/"
rm -f /home/avbravo/jettradbvmnodes/node4/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node4/jettraDBVMShell.jar





echo "Proceso finalizado"