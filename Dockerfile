FROM php:8.3-apache

# Enable Apache mod_rewrite (in case needed later)
RUN a2enmod rewrite

# Copy all webapp folders into the web root
COPY . /var/www/html/

# Ensure the beer orders.json is writable
RUN chown www-data:www-data /var/www/html/beer/orders.json

EXPOSE 80
