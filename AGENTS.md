# AGENTS.md — EazyBank (section_17)

Plataforma de 6 microservicios Spring Boot. Este archivo da contexto estable al
agente de codificacion (OpenCode, u otros que lean AGENTS.md) en cada sesion.
Mantener corto: se carga en el contexto cada vez, cada linea de mas compite con
la tarea actual.

## Proyecto
- 6 microservicios, cada uno con su propio `pom.xml` (NO hay pom padre):
  `accounts`, `cards`, `loans`, `configserver`, `gatewayserver`, `message`.
- Stack: Java 21, Spring Boot 4.x, Spring Cloud 2025.x, Maven (con wrapper `mvnw`).
- Mensajería: Spring Cloud Stream + Kafka binder (productor vía `StreamBridge`).
- Persistencia: JPA/Hibernate. H2 en runtime; MySQL real en tests de integración.
- Infra: Kubernetes (Docker Desktop y GKE), Helm multi-entorno (dev/qa/prod),
  Keycloak (OAuth2/JWT), Redis, Prometheus/Grafana.

## Comandos
- Build + tests de un módulo: `./mvnw -B -ntp verify` (desde la carpeta del micro).
- Tests unitarios (`*Test`) corren con Surefire en la fase `test`.
- Tests de integración (`*IT`, Testcontainers) corren con Failsafe en la fase `verify`.
- NO usar `mvn` del sistema; usar siempre el wrapper `./mvnw` (reproducibilidad).

## Convenciones
- Commits: Conventional Commits en inglés, con scope. Ej: `test(accounts): ...`,
  `chore(deps): ...`, `fix(cards,loans): ...`.
- Controllers: inyección por constructor (Lombok `@RequiredArgsConstructor`, campos
  `final`). NO usar `@Autowired` en campo.
- Tests de controllers Spring MVC: patrón `@WebMvcTest` + `@MockitoBean`.
- Errores de validación de `@RequestParam` (`@Pattern`) deben devolver 400, no 500.

## Gotchas conocidos (Spring Boot 4.x)
- `@EnableJpaAuditing` debe ir FUERA de la clase `@SpringBootApplication`, o los
  slice tests (`@WebMvcTest`, `@DataJpaTest`) intentan cargar el `EntityManagerFactory`
  y fallan.
- El test-binder de Spring Cloud Stream 5.0.0 ya NO se autorregistra por nombre; hay
  que activarlo con `@EnableTestBinder` en la clase de test.
- Testcontainers en local (Windows/Docker Desktop): la config va en el
  `maven-failsafe-plugin` del pom (`DOCKER_HOST` como env var, `api.version` como
  system property). No escribirla desde el código del test.

## Cómo trabajar conmigo (preferencias)
- Conversación en español. Commits siempre en inglés.
- Para builds de imágenes, deploy, `docker`, `git push` y scripts `.ps1`:
  PROPONER un plan y NO ejecutar; yo lo ejecuto manualmente y verifico la salida.
- Léxico: usar "ejecución" o "run", evitar "corrida".
- Explicar el porqué de los cambios, no solo aplicarlos.

## Qué NO hacer
- No commitear `target/`, `.vscode/` ni secretos.
- No cambios masivos automáticos sin revisarlos uno a uno.
- No subir cobertura de módulos ya cubiertos sin que yo lo pida (accounts/cards/loans
  ya están ~96%).
