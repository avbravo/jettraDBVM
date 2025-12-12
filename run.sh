echo "compilando proyecto"

mvn package -DskipTests

echo "ejecutando "

java -jar target/jettraDBVM-1.0-SNAPSHOT.jar 
