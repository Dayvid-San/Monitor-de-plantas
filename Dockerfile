# Backend + dashboard numa imagem só. Build a partir da raiz do repositório:
#   docker build -t plant-monitor .
#   docker run -d --restart unless-stopped -p 3000:3000 -v plant-data:/app/backend/data \
#     -e DEVICE_API_KEY=troque-esta-chave plant-monitor
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -B -f backend/pom.xml dependency:go-offline
COPY backend backend
RUN mvn -q -B -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
# O backend serve o frontend de ../frontend (relativo ao diretório de trabalho).
WORKDIR /app/backend
COPY --from=build /src/backend/target/plant-monitor-backend.jar .
COPY frontend /app/frontend
ENV PORT=3000 \
    JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
VOLUME /app/backend/data
EXPOSE 3000
CMD ["java", "-jar", "plant-monitor-backend.jar"]
