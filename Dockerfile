FROM php:8.3-apache

# Enable required Apache modules
RUN a2enmod rewrite headers proxy proxy_fcgi proxy_http

# Apache security hardening
COPY apache-security.conf /etc/apache2/conf-enabled/security.conf
COPY simgolf-proxy.conf /etc/apache2/conf-enabled/simgolf-proxy.conf
COPY webapp-proxy.conf /etc/apache2/conf-enabled/webapp-proxy.conf
COPY book-log-proxy.conf /etc/apache2/conf-enabled/book-log-proxy.conf
COPY dreamworld-proxy.conf /etc/apache2/conf-enabled/dreamworld-proxy.conf

# Disable PHP version header
RUN echo "expose_php = Off" > /usr/local/etc/php/conf.d/security.ini

# Create data directories outside web root
RUN mkdir -p /var/www/data/beer /var/www/data/dreamworld \
 && chown www-data:www-data /var/www/data/beer /var/www/data/dreamworld

# Copy all webapp folders into the web root
COPY . /var/www/html/

EXPOSE 80
