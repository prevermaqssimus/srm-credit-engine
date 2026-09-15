# =============================================================================
# STAGE 1: BUILD -- compila o codigo Java e gera o .jar executavel.
# Usa uma imagem que ja vem com Maven + Java 21 instalados.
# "AS build" da um nome a essa etapa, para referenciar depois no Stage 2.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

# Define a pasta de trabalho dentro do container -- tudo daqui pra frente
# acontece relativo a /app.
WORKDIR /app

# Copia SO o pom.xml primeiro (ainda nao o codigo) -- otimizacao de cache:
# se so o codigo mudar depois (nao as dependencias), o Docker reaproveita
# esse passo de download, sem baixar tudo de novo a cada build.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# So agora copia o codigo-fonte inteiro, e compila/empacota o .jar.
# -DskipTests: os testes ja rodam no CI -- repetir aqui so deixa o build
# mais lento, sem beneficio adicional.
COPY src ./src
RUN mvn -B -ntp package -DskipTests

# =============================================================================
# STAGE 2: RUNTIME -- a imagem final, de verdade, que sera distribuida/rodada.
# Comeca DO ZERO, com uma imagem bem mais leve: so o JRE (Java Runtime, pra
# RODAR java), sem o Maven nem o JDK completo usado so para compilar.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Seguranca: por padrao, containers rodam como usuario root (administrador).
# Criamos um usuario comum e trocamos para ele -- limita o dano em caso de
# uma vulnerabilidade explorada dentro da aplicacao.
RUN addgroup -S srm && adduser -S srm -G srm
USER srm

# Copia SO o .jar final do Stage 1 (--from=build) -- nada do Maven, do
# codigo-fonte, ou das dependencias de build "vaza" para a imagem final.
COPY --from=build /app/target/creditengine-0.0.1-SNAPSHOT.jar app.jar

# Documenta que a aplicacao escuta na porta 8080 (nao abre a porta sozinho --
# quem faz isso de fato e o docker-compose.yml, com "ports:").
EXPOSE 8080

# Comando executado quando o container inicia -- equivalente a rodar
# "java -jar app.jar" manualmente.
ENTRYPOINT ["java", "-jar", "app.jar"]