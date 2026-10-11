# Single-service runtime image for logistics-os (built by .github/workflows/images.yml).
# The OTel java agent is downloaded into ./otel/ by the workflow and baked in so every image
# ships with tracing enabled; the k8s ConfigMap `logistics-os-env` overrides the OTLP endpoint
# and resource attributes per environment.
FROM eclipse-temurin:21-jre

ARG JAR

COPY otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY ${JAR} /app/service.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"
ENV OTEL_JAVAAGENT_ENABLED=true
ENV OTEL_SERVICE_NAME="logistics-os"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector.logistics-os.svc.cluster.local:4317"

ENTRYPOINT ["java", "-jar", "/app/service.jar"]