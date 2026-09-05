# Printshop (Print Service)

Core print service for the printshop platform: accounts, orders, file upload,
stores and service items. Runs on port `8081` behind the gateway.

Build from the backend root (single Maven reactor for all services):

```
# inside printshop/
../mvnw -DskipTests package

# or from the backend root for the whole reactor
mvnw clean package -DskipTests
python scripts/package_java_services.py
```

Main areas:

- `controller/`: REST endpoints (`/api/print/**` upstream of the gateway rewrite).
- `service/` + `service/impl/`: business logic for orders, files, accounts, stores.
- `mapper/` + `resources/mapper/`: MyBatis interfaces and XML.
- `security/`: JWT, captcha, rate limiting, field crypto, upload protection.
- `bootstrap/`: first superadmin bootstrap and email identity migration.

`files/` holds runtime uploads when running outside Docker; the Docker
deployment mounts `backend/data/files` instead.
