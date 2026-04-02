FROM php:8.3-apache

# Enable required Apache modules
RUN a2enmod rewrite headers

# Apache security hardening
COPY apache-security.conf /etc/apache2/conf-enabled/security.conf

# Disable PHP version header
RUN echo "expose_php = Off" > /usr/local/etc/php/conf.d/security.ini

# Create data directory outside web root
RUN mkdir -p /var/www/data/beer && chown www-data:www-data /var/www/data/beer

# Copy all webapp folders into the web root
COPY . /var/www/html/

EXPOSE 80
