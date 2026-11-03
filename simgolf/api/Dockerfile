FROM php:8.3-fpm-alpine

RUN apk add --no-cache libpq-dev \
    && docker-php-ext-install pdo pdo_pgsql

WORKDIR /var/www/api

# Keep production independent of source bind mounts in the deployment checkout.
COPY --chown=www-data:www-data public/ ./public/
COPY --chown=www-data:www-data src/ ./src/
