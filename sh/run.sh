echo "Iniciando servidor JettraDB..."

while true; do
  # Intentar encontrar el JAR autodetectando el nombre (jettraDBVM o jettraFederated)
  JAR_FILE=""
  for name in "jettraDBVM.jar" "jettraFederated.jar" "jettra-server.jar" "jettra-federated.jar"; do
    if [ -f "target/$name" ]; then
      JAR_FILE="target/$name"
      break
    elif [ -f "$name" ]; then
      JAR_FILE="$name"
      break
    fi
  done

  if [ -n "$JAR_FILE" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Iniciando $JAR_FILE..."
    JETTRAMODE=SUPERVISED java -Xmx256m -Xms64m -XX:+UseCompactObjectHeaders -Djettra.mode=supervised -jar "$JAR_FILE" "$@"
    EXIT_CODE=$?
  else
    echo "Error: No se encontró el archivo JAR en target/ ni en el directorio actual."
    exit 1
  fi

  if [ $EXIT_CODE -eq 3 ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Reiniciando servidor (código 3 detectado)..."
    sleep 2 # Esperar un poco más para asegurar que el puerto se libere
    continue
  fi

  if [ $EXIT_CODE -ne 0 ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Servidor terminó con error (código $EXIT_CODE)."
    # Opcional: podrías decidir reiniciar aquí también si es un error de puerto
    # Pero por ahora seguimos el deseo del usuario de "reinicio automático" controlado
  fi

  echo "$(date '+%Y-%m-%d %H:%M:%S') - Servidor detenido (código $EXIT_CODE). Saliendo del script de ejecución."
  exit $EXIT_CODE
done
