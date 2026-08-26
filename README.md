# Thumbnailer — an AWS Elastic Beanstalk Java sample

A small Java web application that resizes images. Give it a picture and it returns three
thumbnails. It exists to be deployed: everything in it earns its place by making something
Elastic Beanstalk does visible.

There is **no Dockerfile, no Kubernetes manifest, and no infrastructure code** in this
repository. That absence is the point — Elastic Beanstalk builds the container image and creates
the environment from the source you upload.

## What it demonstrates

| Elastic Beanstalk behaviour | How you can see it |
|---|---|
| Building a container image from source | No Dockerfile here; Beanstalk detects Java and builds one |
| `PORT` injection | The app binds `${PORT:8080}`; `/info` reports the port it bound |
| Health checks that mean something | `/health` verifies a JPEG writer exists, so it can genuinely fail |
| Rolling updates with no downtime | Every response names the version and replica that served it |
| Load balancing across replicas | `servedBy` changes as you refresh |
| Autoscaling | `GET /api/load` burns CPU on demand |
| Automatic instrumentation | Set the application language to Java; logs, metrics, and traces flow with no code change |
| Environment properties | `/info` reports the Region, which Beanstalk does *not* inject for you |

## The application

| Endpoint | Purpose |
|---|---|
| `GET /` | Picker for the built-in samples, plus an upload field |
| `GET /health` | Readiness and liveness. 200 when a JPEG writer is available, 503 otherwise |
| `GET /info` | Version, replica hostname, uptime, bound port, Region, heap, thumbnails generated |
| `GET /api/samples` | The built-in sample images |
| `GET /api/samples/{id}/preview` | A reduced copy of a sample, for the picker |
| `POST /api/samples/{id}/thumbnails` | Resize a built-in sample |
| `POST /api/thumbnails` | Resize an uploaded image (multipart field `file`, 5 MB limit) |
| `GET /api/load?seconds=10` | Saturate the CPU for up to 30 seconds |

Requests are handled entirely in memory. Nothing is written to disk and nothing is kept between
requests, so any replica can serve any request and no database is needed.

The sample images are drawn at startup rather than committed here, which keeps this a text-only
repository with no binary assets and no image licensing to account for.

## Run it locally

Requires JDK 21 or later.

```bash
mvn package
java -jar target/thumbnailer-1.0.0.jar
```

Then open <http://localhost:8080>. To use a different port, set `PORT`:

```bash
PORT=8099 java -jar target/thumbnailer-1.0.0.jar
```

```bash
curl -s localhost:8080/health
curl -s localhost:8080/info
curl -s -X POST localhost:8080/api/samples/gradient/thumbnails
curl -s -X POST -F "file=@your-photo.jpg" localhost:8080/api/thumbnails
```

## Deploy to Elastic Beanstalk

### Prerequisites

Three IAM roles, which Elastic Beanstalk assumes on your behalf:

| Role | Used for |
|---|---|
| Cluster role | The managed compute environment Beanstalk creates |
| Node role | The compute that runs your application |
| Observability role | Delivering logs, metrics, and traces. Trusts `pods.eks.amazonaws.com` |

Building a container image from source may require an additional build role. Check the current
documentation before you start.

### From the console

1. Zip this repository, or download it as a .zip from your Git host.
2. Choose **Create environment**, then the **Cluster** deployment type.
3. **Application code**: upload the .zip.
4. **Service port**: `8080`.
5. **Environment properties**: add `AWS_REGION` set to your Region.
6. Select the three roles above.
7. **Health check path**: `/health`.
8. **Application language**: Java, which turns on automatic instrumentation.
9. **Scaling**: 1 to 3 replicas, CPU threshold 75%.

`eb/option-settings.json` holds the same configuration for the CLI. Replace the three
`*_ROLE_ARN` placeholders and `__AWS_REGION__` before using it.

### From the CLI

```bash
aws elasticbeanstalk create-application --application-name thumbnailer

# Create an application version from this source. Confirm the current syntax for source
# bundles and build configuration against the documentation, or copy the commands the
# console shows in its review panel.

aws elasticbeanstalk create-environment \
  --application-name thumbnailer \
  --environment-name thumbnailer-env \
  --tier Name=Kubernetes,Type=Standard \
  --version-label 1.0.0 \
  --option-settings file://eb/option-settings.json
```

Watch it with `describe-events` rather than environment status — status can read `Ready` while a
deployment underneath it has failed.

```bash
aws elasticbeanstalk describe-events \
  --environment-name thumbnailer-env --max-records 20 \
  --query 'Events[*].[EventDate,Severity,Message]' --output table
```

The first environment takes longer than later ones because the shared compute infrastructure is
created for it.

## Watch a rolling update

Every response names the version that produced it, so an update is visible rather than
inferred.

1. Note the version on the page, or in `/info`.
2. Change `<version>` in `pom.xml` from `1.0.0` to `1.1.0`.
3. Re-zip, and deploy the new version to the same environment.
4. Refresh while it rolls out. Traffic keeps being served, `servedBy` moves between replicas,
   and the version flips to `1.1.0`.

If a new version fails its health checks, Beanstalk restores the previous one automatically.

## Trigger autoscaling

```bash
# A few concurrent bursts, enough to push CPU past the threshold.
for i in 1 2 3 4; do curl -s "https://YOUR-ENDPOINT/api/load?seconds=30" & done; wait
```

Then watch the replica count. Saturating every core can also make health checks time out, so
keep the bursts short — a pod that stops answering `/health` gets restarted.

## What is deliberately absent

- **No Dockerfile.** Beanstalk builds the image.
- **No manifests or infrastructure code.** Beanstalk creates the environment.
- **No database.** The app is stateless, so it behaves correctly across several replicas.
- **No AWS SDK calls on the default path**, so no application role is needed to run it.

## Clean up

Terminate the environment from the console, or:

```bash
aws elasticbeanstalk terminate-environment --environment-name thumbnailer-env
```

Shared infrastructure is removed automatically once no environment is using it. Delete any IAM
roles you created only for this sample.

## License

MIT-0. See [LICENSE](LICENSE).
