# Declarative Consul discovery

The JSON files in this directory are declarative Consul agent service
definitions. They are mounted at `/consul/config` in the Consul container, so
Consul registers the services without discovery-client code in the services.
The services only need to expose their `/q/health` endpoints for the configured
HTTP checks.

Clients resolve services through Stork with
`service-discovery.type=consul`. Stork reads the Consul connection settings
from properties such as
`quarkus.stork.<service-name>.service-discovery.consul-host` and
`quarkus.stork.<service-name>.service-discovery.consul-port`. In a container
environment, services should default these values to Consul's Compose DNS name
and port, for example:

```properties
quarkus.stork.product-service.service-discovery.type=consul
quarkus.stork.product-service.service-discovery.consul-host=${CONSUL_HOST:consul}
quarkus.stork.product-service.service-discovery.consul-port=${CONSUL_PORT:8500}
```

The equivalent environment-variable form uses Quarkus name mapping, for
example `QUARKUS_STORK__SERVICE_NAME__SERVICE_DISCOVERY_CONSUL_HOST` for
`quarkus.stork.<service-name>.service-discovery.consul-host` (and the
corresponding `...CONSUL_PORT` variable for the port).

To add a service, copy an existing JSON file, then change the `name` and
`address` values to the new service's logical name and Compose DNS name.
Update the health-check URL to point to that service's `/q/health` endpoint.
