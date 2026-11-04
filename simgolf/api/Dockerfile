FROM php:8.3-fpm-alpine

RUN apk add --no-cache libpq-dev \
    && docker-php-ext-install pdo pdo_pgsql

WORKDIR /var/www/api

# Ship the API with the image; deployment checkout mounts can become stale
# when Komodo replaces the checkout directory during an update.
COPY public/ ./public/
COPY src/ ./src/
COPY bin/ ./bin/
COPY migrations/ ./migrations/
COPY tests/ ./tests/
RUN chmod -R a+rX /var/www/api

COPY docker/zz-logging.conf /usr/local/etc/php-fpm.d/zz-logging.conf
