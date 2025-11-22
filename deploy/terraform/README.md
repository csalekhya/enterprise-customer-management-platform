# Terraform — AWS infrastructure

Infrastructure for the Enterprise Customer Management Platform: a VPC with public, private and
isolated database tiers, an ALB fronting two ECS Fargate services, RDS MySQL multi-AZ, ECR
repositories, Secrets Manager and CloudWatch.

## Layout

| File | Contents |
|---|---|
| `main.tf` | Providers, version pins, ECS cluster, log groups, CloudWatch alarms |
| `variables.tf` | Every input, with validation and a documented default |
| `vpc.tf` | VPC, three subnet tiers, NAT, route tables, PrivateLink endpoints |
| `alb.tf` | Load balancer, target groups, listeners, path-based routing |
| `ecs.tf` | Task definitions, services, security groups, autoscaling policies |
| `rds.tf` | Subnet group, parameter group, MySQL instance, enhanced monitoring |
| `ecr.tf` | Image repositories, lifecycle and repository policies |
| `secrets.tf` | Generated secrets and the two IAM roles |
| `outputs.tf` | URLs, cluster and service names, repository URLs, secret ARNs |

## Prerequisites

- Terraform >= 1.6
- AWS credentials with permission to create VPC, ECS, RDS, ELB, IAM, ECR and Secrets Manager
  resources
- An ACM certificate in the same region if you want HTTPS (optional; see below)

## Usage

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars   # then edit

terraform init
terraform fmt -check -recursive
terraform validate
terraform plan  -out=tfplan
terraform apply tfplan
```

Then build and push the two images, and let ECS roll:

```bash
API_REPO=$(terraform output -raw ecr_api_repository_url)
WEB_REPO=$(terraform output -raw ecr_web_repository_url)
REGION=$(terraform output -raw  aws_region 2>/dev/null || echo us-east-1)

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${API_REPO%/*}"

docker buildx build --platform linux/arm64 -t "$API_REPO:$(git rev-parse --short HEAD)" ../../backend  --push
docker buildx build --platform linux/arm64 -t "$WEB_REPO:$(git rev-parse --short HEAD)" ../../frontend --push

terraform apply -var="api_image_tag=$(git rev-parse --short HEAD)" \
                -var="web_image_tag=$(git rev-parse --short HEAD)"
```

Images must be built for **linux/arm64** — both task definitions request Graviton, which is
cheaper per vCPU and where the JVM performs well.

## Design notes

**Three subnet tiers, not two.** The database subnets have a route table with no default route
at all, so RDS cannot reach the internet and the internet cannot reach it. That is a stronger
statement than a security-group rule, because a routing table has no "allow from anywhere"
foot-gun.

**PrivateLink for ECR, S3, Secrets Manager and Logs.** Every task start-up pulls an image and
reads two secrets. Over a NAT gateway that traffic is billed per gigabyte and adds latency to
every deploy; over an interface endpoint it never leaves the VPC.

**Secrets are generated, not supplied.** `random_password` creates the database password and
the JWT signing key, and the ECS agent injects them via the task definition's `secrets` block.
No credential appears in a task definition, an environment file or the repository. The trade-off
is that the values do live in Terraform state — which is why the S3 backend is encrypted and
access-controlled, and why the state bucket is the most sensitive artifact in this stack.

**Two IAM roles.** The execution role pulls images and reads secrets at start-up; the task role
is what the application runs as and holds no Secrets Manager permission at all, because the
application never reads a secret at runtime.

**Deployment circuit breaker with rollback.** A task that never passes its readiness check rolls
the deployment back automatically instead of leaving a half-deployed service. Combined with
`deployment_minimum_healthy_percent = 100`, a bad deploy never reduces serving capacity.

**Autoscaling on two signals.** CPU target tracking handles ordinary load. A second policy on
`ALBRequestCountPerTarget` covers the case where requests pile up on IO without CPU rising —
usually a slow database — which CPU alone would miss.

**Fargate Spot for the web tier only.** Serving static files is interruption-tolerant, so the
web service runs 1 on-demand task plus a 3:1 Spot weighting. The API stays entirely on-demand:
an interrupted task mid-request is a user-visible error for no meaningful saving at this size.

## Cost

Rough monthly estimate for the staging defaults in `us-east-1`:

| Resource | Estimate |
|---|---|
| ALB | ~$18 + LCU |
| NAT gateway (single) | ~$33 + data |
| ECS Fargate (2 × api @ 1 vCPU/2 GB, 2 × web @ 0.25 vCPU) | ~$75 |
| RDS `db.t4g.medium` multi-AZ + 50 GB gp3 | ~$130 |
| Interface VPC endpoints (4 × 2 AZ) | ~$58 |
| CloudWatch logs, Secrets Manager, ECR | ~$10 |
| **Total** | **~$325/month** |

To run it cheaply as a demo: `db_multi_az = false`, `db_instance_class = "db.t4g.micro"`,
`api_desired_count = 1`, `api_min_capacity = 1`, `web_desired_count = 1`, and delete the
interface endpoints. That lands near $70/month.

## Teardown

```bash
terraform destroy
```

In `prod` this refuses until `db_deletion_protection` and `alb_deletion_protection` are set to
`false`, and it takes a final RDS snapshot. Secrets have a 30-day recovery window in `prod` and
zero elsewhere, so a destroy/apply cycle in staging does not collide with a scheduled deletion.
