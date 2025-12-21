echo "compilando proyecto"

mvn package -DskipTests

echo "ejecutando "

while true; do
  java -XX:+UseCompactObjectHeaders -jar target/jettraDBVM.jar "$@"
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 3 ]; then
    echo "Servidor detenido (código $EXIT_CODE). Saliendo..."
    exit $EXIT_CODE
  fi
  echo "Reiniciando servidor..."
  sleep 1
done
