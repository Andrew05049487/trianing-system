FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests && \
    find target -maxdepth 1 -name "*.jar" ! -name "*.original" \
    -exec cp {} /app/app.jar \;

FROM tailscale/tailscale:v1.102.5 AS tailscale

FROM eclipse-temurin:21-jre

# Userspace mode requires neither /dev/net/tun nor NET_ADMIN.
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

COPY --from=tailscale /usr/local/bin/tailscale /usr/local/bin/tailscale
COPY --from=tailscale /usr/local/bin/tailscaled /usr/local/bin/tailscaled

WORKDIR /app

COPY --from=build /app/app.jar app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
# Windows checkouts may use CRLF. The runtime script must be Unix-compatible.
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 755 /app/entrypoint.sh

EXPOSE 10000

ENTRYPOINT ["/app/entrypoint.sh"]
