# --- build ---------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# .npmrc fait partie des fichiers d'installation : il porte legacy-peer-deps=true, sans quoi
# « npm ci » refuse le conflit de peer dependency PrimeNG 17 / Angular 19. L'oublier ici casse
# la construction de l'image alors que la CI et le poste, eux, passent.
COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .
RUN npx ng build --configuration production

# --- serve ---------------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/real-estate-frontend/browser /usr/share/nginx/html

EXPOSE 80
