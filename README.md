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
| Autoscaling | `GET /api/load` burns CPU on demand, once you turn it on |
| Automatic instrumentation | Set the application language to Java; logs, metrics, and traces flow with no code change |
| Environment properties | `/info` reports the Region, which Beanstalk does *not* inject for you |

## The application

| Endpoint | Purpose |
|---|---|
| `GET /` | Picker for the built-in samples, plus an upload field |
| `GET /health` | Readiness and liveness. 200 when a JPEG writer is available, 503 otherwise |
| `GET /info` | Version, replica hostname, uptime, bound port, Region, heap, thumbnails generated, whether `/api/load` is open |
| `GET /api/samples` | The built-in sample images |
| `GET /api/samples/{id}/preview` | A reduced copy of a sample, for the picker |
| `POST /api/samples/{id}/thumbnails` | Resize a built-in sample |
| `POST /api/thumbnails` | Resize an uploaded image (multipart field `file`, 5 MB and 25 MP limits) |
| `GET /api/load?seconds=10` | Saturate the CPU for up to 30 seconds. 404 unless enabled — see [Trigger autoscaling](#trigger-autoscaling) |

Requests are handled entirely in memory. Nothing is written to disk and nothing is kept between
requests, so any replica can serve any request and no database is needed.

The sample images are drawn at startup rather than committed here, which keeps this a text-only
repository with no binary assets and no image licensing to account for.

## This is a sample, not a production service

It exists to make Elastic Beanstalk behaviour visible and to be read. Deployed as documented it is
reachable from the internet with nothing authenticating in front of it, so read everything it
serves as public.

- **No authentication and no rate limiting.** Any caller can spend your CPU resizing images.
  Terminate the environment when you are finished with it.
- **`GET /api/load` is off unless you ask for it.** It saturates every core on the replica that
  serves it, which on a public endpoint is a denial-of-service tool rather than a demonstration.
  Set `LOAD_ENDPOINT_ENABLED=true` to turn it on for a scaling demo, and unset it afterwards.
  `/info` reports whether it is on.
- **Uploads are bounded twice**: 5 MB per file, and 25 megapixels per image. The pixel count is
  read from the image header and checked before anything is decoded, because bytes on the wire do
  not bound memory — a few megabytes of PNG can describe hundreds of megapixels, and decoding
  allocates four bytes for every one of them.

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

Four IAM roles, which Elastic Beanstalk assumes on your behalf:

| Role | Trusted by | Used for |
|---|---|---|
| Cluster role | `eks.amazonaws.com` | The managed compute environment Beanstalk creates |
| Node role | `ec2.amazonaws.com` | The compute that runs your application |
| Observability role | `pods.eks.amazonaws.com` | Delivering logs, metrics, and traces |
| Image build role | `codebuild.amazonaws.com` | Building the container image from this source |

The image build role needs the `AWSElasticBeanstalkEKSImageBuild` managed policy. It is required
for any source deployment — without it, the image cannot be built.

You also choose a **builder image**: a Cloud Native Buildpack builder that detects your language
and packages the application. For Java on `amd64`, use `paketobuildpacks/builder-jammy-base`.

### From the console

1. Zip this repository, or download it as a .zip from your Git host.
2. Choose **Create environment**, then the **Cluster** deployment type.
3. **Application code**: upload the .zip.
4. **Service port**: `8080`.
5. **Environment properties**: add `AWS_REGION` set to your Region. Add
   `LOAD_ENDPOINT_ENABLED` set to `true` only if you intend to run the autoscaling demo.
6. Select the three roles above.
7. **Health check path**: `/health`.
8. **Application language**: Java, which turns on automatic instrumentation.
9. **Scaling**: 1 to 3 replicas, CPU threshold 75%.

`eb/option-settings.json` holds the same configuration for the CLI. Replace the three
`*_ROLE_ARN` placeholders and `__AWS_REGION__` before using it.

### From the CLI

Upload the source, then create an application version that tells Elastic Beanstalk to build it
with a buildpack:

```bash
BUCKET=elasticbeanstalk-<region>-<account-id>
BUILD_ROLE=arn:aws:iam::<account-id>:role/aws-elasticbeanstalk-eks-image-build-role

zip -r thumbnailer.zip . -x 'target/*' '.git/*'
aws s3 cp thumbnailer.zip "s3://$BUCKET/thumbnailer/thumbnailer-1.0.0.zip"

aws elasticbeanstalk create-application --application-name thumbnailer

cat > build-configuration.json <<EOF
{
  "CodeBuildServiceRole": "$BUILD_ROLE",
  "ComputeType": "BUILD_GENERAL1_MEDIUM",
  "TimeoutInMinutes": 30,
  "ImageBuildConfiguration": {
    "Type": "buildpack",
    "Buildpack": "paketobuildpacks/builder-jammy-base"
  }
}
EOF

aws elasticbeanstalk create-application-version \
  --application-name thumbnailer --version-label 1.0.0 \
  --source-bundle "S3Bucket=$BUCKET,S3Key=thumbnailer/thumbnailer-1.0.0.zip" \
  --build-configuration file://build-configuration.json

aws elasticbeanstalk create-environment \
  --application-name thumbnailer \
  --environment-name thumbnailer-env \
  --tier Name=Kubernetes,Type=Standard \
  --version-label 1.0.0 \
  --option-settings file://eb/option-settings.json
```

The version is created as `UNPROCESSED`; the image is built when an environment first uses it.

Do not set `BuildConfiguration.Image` — the service selects a compatible build image and rejects
the field if you supply it.

Watch it with `describe-events` rather than environment status — status can read `Ready` while a
deployment underneath it has failed.

```bash
aws elasticbeanstalk describe-events \
  --environment-name thumbnailer-env --max-records 20 \
  --query 'Events[*].[EventDate,Severity,Message]' --output table
```

The first environment takes longer than later ones because the shared compute infrastructure is
created for it. Once that exists, a build and deploy of this application completes in a few
minutes.

Observed on a verified deployment: the application started in 4.2 seconds on 2 vCPU, the JVM was
given a 1442 MB heap inside a 2 Gi limit, and a 1600x1200 source resized to three thumbnails in
54-86 ms. The endpoint served HTTPS on 443 with a certificate provisioned automatically; port 80
was closed.

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

`GET /api/load` returns 404 until you enable it. `env-variables` carries the whole property map, so
pass the Region in the same call or you will remove it:

```bash
aws elasticbeanstalk update-environment --environment-name thumbnailer-env \
  --option-settings 'Namespace=aws:elasticbeanstalk:eks:environment,OptionName=env-variables,Value={"AWS_REGION":"us-west-2","LOAD_ENDPOINT_ENABLED":"true"}'
```

```bash
# A few concurrent bursts, enough to push CPU past the threshold.
for i in 1 2 3 4; do curl -s "https://YOUR-ENDPOINT/api/load?seconds=30" & done; wait
```

Then watch the replica count. Saturating every core can also make health checks time out, so
keep the bursts short — a pod that stops answering `/health` gets restarted.

Apply the same option setting without `LOAD_ENDPOINT_ENABLED` to close the endpoint again. Running
locally, it is an ordinary environment variable:

```bash
LOAD_ENDPOINT_ENABLED=true java -jar target/thumbnailer-1.0.0.jar
```

## What is deliberately absent

- **No Dockerfile.** Beanstalk builds the image.
- **No manifests or infrastructure code.** Beanstalk creates the environment.

## Clean up

Terminate the environment from the console, or:

```bash
aws elasticbeanstalk terminate-environment --environment-name thumbnailer-env
```

Shared infrastructure is removed automatically once no environment is using it. Delete any IAM
roles you created only for this sample.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for how to report a vulnerability.
Please do not open a public issue for one.

## License

MIT-0. See [LICENSE](LICENSE).
