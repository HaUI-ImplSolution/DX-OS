# Keycloak from its release archive on Ubuntu, so it also starts on x86-64 CPUs without
# x86-64-v2 (SSE4.2, POPCNT), such as the "Common KVM processor" of some VPS hosts.
FROM eclipse-temurin:25-jre-jammy AS dist
ARG KEYCLOAK_VERSION=26.8.2
ARG KEYCLOAK_SHA1=7918de2786c7db466152128bea350becbd40a58f
ADD https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz /tmp/keycloak.tar.gz
RUN echo "${KEYCLOAK_SHA1}  /tmp/keycloak.tar.gz" | sha1sum -c - \
    && mkdir -p /opt/keycloak \
    && tar -xzf /tmp/keycloak.tar.gz -C /opt/keycloak --strip-components=1 \
    && mkdir -p /opt/keycloak/data \
    && chmod -R g+rwX /opt/keycloak

FROM eclipse-temurin:25-jre-jammy
RUN useradd --uid 1000 --gid 0 --home-dir /opt/keycloak --no-create-home --shell /usr/sbin/nologin keycloak
COPY --from=dist --chown=1000:0 /opt/keycloak /opt/keycloak
USER 1000
EXPOSE 8080 8443 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
