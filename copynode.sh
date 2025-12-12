echo "generando "

mvn clean package -DskipTests

echo "Copiando jettraDBVM.jar a /node1"
rm -f /home/avbravo/nodosjettradbvm/node1/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/nodosjettradbvm/node1/jettraDBVM.jar

echo "Copiando jettraDBVM.jar a /node2"
rm -f /home/avbravo/nodosjettradbvm/node2/jettraDBVM.jar
cp jettra-server/target/jettraDBVM.jar /home/avbravo/nodosjettradbvm/node2/jettraDBVM.jar

echo "Generating shell..."
cp jettra-shell/target/jettraDBVMShell.jar .


echo "Copiando JettraDBVMShell a /nodosjettradbvm/node1/"
rm -f /home/avbravo/nodosjettradbvm/node1/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/nodosjettradbvm/node1/jettraDBVMShell.jar


echo "Copiando JettraDBVMShell a /nodosjettradbvm/node2/"
rm -f /home/avbravo/nodosjettradbvm/node2/jettraDBVMShell.jar
cp jettra-shell/target/jettraDBVMShell.jar /home/avbravo/nodosjettradbvm/node2/jettraDBVMShell.jar


echo "Proceso finalizado"