echo "compilando proyecto"

mvn package -DskipTests

echo "ejecutando "

java -XX:+UseCompactObjectHeaders -jar target/jettraDBVM-1.0-SNAPSHOT.jar 
