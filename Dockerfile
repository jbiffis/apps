FROM php:8.3-apache

# Enable required Apache modules
RUN a2enmod rewrite headers proxy proxy_fcgi proxy_http

# Apache security hardening
COPY apache-security.conf /etc/apache2/conf-enabled/security.conf
COPY simgolf-proxy.conf /etc/apache2/conf-enabled/simgolf-proxy.conf
COPY webapp-proxy.conf /etc/apache2/conf-enabled/webapp-proxy.conf
COPY bookstory-proxy.conf /etc/apache2/conf-enabled/bookstory-proxy.conf
COPY dreamworld-proxy.conf /etc/apache2/conf-enabled/dreamworld-proxy.conf
COPY tracker-proxy.conf /etc/apache2/conf-enabled/tracker-proxy.conf

# Disable PHP version header
RUN echo "expose_php = Off" > /usr/local/etc/php/conf.d/security.ini

# Create data directories outside web root
RUN mkdir -p /var/www/data/beer /var/www/data/dreamworld /var/www/data/golf/rounds \
 && chown -R www-data:www-data /var/www/data/beer /var/www/data/dreamworld /var/www/data/golf

# Copy all webapp folders into the web root
COPY . /var/www/html/

# Map each frontend build to its production URL path.
# COPY . above puts tracker/frontend/dist/ at /var/www/html/tracker/frontend/dist/
# but Apache serves from /var/www/html/tracker/, so we copy the contents there directly.
COPY simgolf/frontend/dist/ /var/www/html/simgolf/
COPY tracker/frontend/dist/ /var/www/html/tracker/
COPY book-log/dist/ /var/www/html/bookstory/
COPY dreamworld/dist/ /var/www/html/dreamworld/

EXPOSE 80
