# Helm

## Helm Chart

[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/fddb-exporter)](https://artifacthub.io/packages/helm/fddb-exporter/fddb-exporter)

The official Helm Chart can be used to deploy the FDDB-Exporter application to a Kubernetes cluster.

```
helm install fddb-exporter oci://ghcr.io/itobey/charts/fddb-exporter --version 1.1.1
```

Or checkout the [Fddb-Exporter Chart](https://github.com/itobey/charts/tree/master/fddb-exporter) yourself.

To see an example of how to use the Helm Chart in an umbrella chart for deployment
with [ArgoCD](https://argo-cd.readthedocs.io/en/stable/), you can check
out [this repository](https://github.com/itobey/k3s-nuc/blob/master/deploy/fddb-exporter/Chart.yaml)

Currently the Helm Chart only deploys the application and does not include any additional services like MongoDB and
InfluxDB.

## Configuration

The following configuration options are an excerpt from the `values.yaml` file to configure the Helm Chart.
The full list of available options can be found in
the [values.yaml](https://artifacthub.io/packages/helm/fddb-exporter/fddb-exporter?modal=values) file.

### Secret Management

To authenticate with FDDB, MongoDB, and InfluxDB, you have two options:

1. Using `secretRef`:
   You can reference an existing Kubernetes secret by specifying the secretRef option. Use secretRef.name to point to
   the desired secret.

2. Using `username` and `password`:
   Alternatively, you can provide credentials directly using the username and password options. If this method is used,
   a new Kubernetes secret containing the password will be created automatically.

Important:
If both `secretRef` and direct username/password options are provided, the `secretRef` will take precedence.

Any additional configuration options for the application will be stored in a ConfigMap. Both secrets and ConfigMaps will
be exposed as environment variables and mounted into the container for access.

```yaml
mongodb:
  enabled: true
  host: "localhost"
  port: "27017"
  database: "database"
  username: "username"
  password: "password"
  secretRef:
    name: ""

influxdb:
  enabled: false
  url: "http://localhost:8086"
  org: "primary"
  bucket: "fddb-exporter"
  token: "token"
  secretRef:
    name: ""

fddb:
  auth:
    username: "username"
    password: "password"
    secretRef:
      name: ""

timezone: "Europe/Berlin"
```