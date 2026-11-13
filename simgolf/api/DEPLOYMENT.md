# PHP deployment and diagnostics

The production `simgolf-php` image includes its source code, migration scripts
and tests. The main Compose stack no longer mounts source from the Komodo
checkout: replacing that checkout during deployment can leave running
containers attached to an old or empty source directory.

After deploying these changes, rebuild and recreate `simgolf-php` in Komodo.
This installs the source files and new FPM logging configuration in the image.
The existing database and environment configuration are unchanged. The
standalone development Compose stack still supports its source bind mount.

FPM access logs include the original request URI, status, actual PHP script
filename and duration. Worker errors also appear in the container logs. For
example:

```text
remote=... time=... method=GET uri=/simgolf/api/seasons status=200 script=/var/www/api/public/index.php duration_ms=...
```

On the Docker host:

```sh
sudo docker logs --timestamps --tail 200 biffis-apps-simgolf-php-1
sudo docker logs --timestamps --tail 200 biffis-apps-web-1
sudo docker inspect --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}' biffis-apps-simgolf-php-1
sudo docker exec -u www-data biffis-apps-simgolf-php-1 sh -c 'test -r /var/www/api/public/index.php && echo "API script readable"'
curl -i https://apps.biffis.com/simgolf/api/seasons
```

`File not found.` from PHP-FPM means its script path is missing or unreadable.
A JSON `{"error":"Not found"}` comes from the application router. The default
FPM log's abbreviated `GET /simgolf/api` does not by itself prove that the
original URL was truncated; use the explicit `uri` and `script` fields.

## Enable detailed logging on an existing container

This temporary override can be applied before deploying the new image. It is
removed automatically when the container is recreated.

```sh
sudo docker exec -i biffis-apps-simgolf-php-1 sh -c 'cat > /usr/local/etc/php-fpm.d/zzzz-diagnostics.conf' <<'EOF'
[www]
access.log = /proc/self/fd/2
access.format = "method=%m uri=%{REQUEST_URI}e status=%s script=%f duration_ms=%{milli}d"
catch_workers_output = yes
decorate_workers_output = no
EOF

sudo docker exec biffis-apps-simgolf-php-1 php-fpm -t && sudo docker exec biffis-apps-simgolf-php-1 kill -USR2 1
curl -i https://apps.biffis.com/simgolf/api/seasons
sudo docker logs --timestamps --tail 50 biffis-apps-simgolf-php-1
```
