# Reverse proxy

The deployed frontend image uses `frontend/nginx.conf`. It serves the Vite build, falls back to `index.html` for client-side routes, and proxies `/api/` to the Compose service name `backend`. In local Vite development, the Vite proxy sends `/api` requests to `localhost:8080` instead.
