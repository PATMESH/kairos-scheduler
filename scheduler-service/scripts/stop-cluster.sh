JAR="target/scheduler-service-0.0.1-SNAPSHOT.jar"

echo "Stopping all cluster nodes..."
pkill -f "$JAR" 2>/dev/null || true
sleep 2