# Muzikant-backend

Spring Boot 3 / Java 21 backend that uses a single host Spotify account and exposes `POST /api/playlist`.

## Environment

Configure `application.properties`:

```properties
spotify.client.id=your_client_id
spotify.client.secret=your_client_secret
spotify.redirect.uri=http://127.0.0.1:8080/oauth/callback
spotify.refresh.token=
spotify.oauth.enabled=false
```

## Run

From the project root:

```bash
SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

On first run, visit:

```
http://localhost:8080/oauth/login
```

Copy the logged refresh token into `spotify.refresh.token` in `application.properties` and restart.

## Swagger / OpenAPI

- UI: `http://127.0.0.1:8080/swagger-ui/index.html`
- Spec: `http://127.0.0.1:8080/v3/api-docs`
