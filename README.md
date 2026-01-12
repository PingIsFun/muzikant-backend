# Muzikant-backend

Spring Boot 3 / Java 21 backend that uses a single host Spotify account and exposes `POST /api/playlist`.

## Environment

Create or edit `.env`:

```env
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=http://127.0.0.1:8080/oauth/callback
SPOTIFY_REFRESH_TOKEN=
FRONTEND_ORIGIN=http://localhost:3000
```

## Load `.env` and run

From the project root:

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

On first run, visit:

```
http://localhost:8080/oauth/login
```

Copy the logged refresh token into `SPOTIFY_REFRESH_TOKEN` and restart.

## Swagger / OpenAPI

- UI: `http://127.0.0.1:8080/swagger-ui/index.html`
- Spec: `http://127.0.0.1:8080/v3/api-docs`
