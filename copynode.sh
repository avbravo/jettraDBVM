echo "generando "

mvn clean package -DskipTests

echo "Copiando jettraDBVM.jar a /node1"
rm -f /home/avbravo/jettradbvmnodes/node1/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node1/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node2"
rm -f /home/avbravo/jettradbvmnodes/node2/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node2/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node3"
rm -f /home/avbravo/jettradbvmnodes/node3/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/jettradbvmnodes/node3/jettraDBVM.jar

echo "Generating shell..."
cp jettra-shell/target/jettraDBVMShell.jar .


echo "Copiando JettraDBVMShell a /jettradbvmnodes/node1/"
rm -f /home/avbravo/jettradbvmnodes/node1/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node1/jettraDBVMShell.jar


echo "Copiando JettraDBVMShell a /jettradbvmnodes/node2/"
rm -f /home/avbravo/jettradbvmnodes/node2/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node2/jettraDBVMShell.jar

echo "Copiando JettraDBVMShell a /jettradbvmnodes/node3/"
rm -f /home/avbravo/jettradbvmnodes/node3/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/jettradbvmnodes/node3/jettraDBVMShell.jar


echo "Proceso finalizado"