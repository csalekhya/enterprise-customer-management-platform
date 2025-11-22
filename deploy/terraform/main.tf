###############################################################################
# Enterprise Customer Management Platform - AWS infrastructure
#
# Topology: ALB in public subnets -> two ECS Fargate services (web + api) in
# private subnets -> RDS MySQL in isolated database subnets. Secrets in Secrets
# Manager, logs in CloudWatch, images in ECR.
#
# The root module is intentionally thin: it pins versions, creates the shared
# cluster and the log groups, and lets the per-concern files own their resources.
###############################################################################

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state with DynamoDB locking. Commented so that `terraform init
  # -backend=false` works in CI and so a reviewer can plan locally; fill in the
  # bucket for a real deployment.
  #
  # backend "s3" {
  #   bucket         = "slate-crm-tfstate"
  #   key            = "crm/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "slate-crm-tf-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
    Repository  = "github.com/csalekhya/enterprise-customer-management-platform"
  }

  account_id = data.aws_caller_identity.current.account_id
}

###############################################################################
# ECS cluster
###############################################################################

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = {
    Name = "${local.name_prefix}-cluster"
  }
}

# Fargate Spot is allowed for the stateless web tier only; see ecs.tf for how the
# capacity providers are weighted per service.
resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

###############################################################################
# CloudWatch log groups
#
# Created here rather than implicitly by ECS so that retention and encryption are
# declared, and so `terraform destroy` actually removes them.
###############################################################################

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name_prefix}/api"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-api-logs"
  }
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/ecs/${local.name_prefix}/web"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-web-logs"
  }
}

###############################################################################
# Alarms
#
# Two that actually page: sustained API 5xx from the load balancer's point of
# view, and database connection saturation - the two failure modes that a rolling
# deploy will not fix by itself.
###############################################################################

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  alarm_name          = "${local.name_prefix}-api-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  treat_missing_data  = "notBreaching"
  alarm_description   = "API target group returned more than 10 5xx responses per minute for two minutes"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${local.name_prefix}-rds-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = var.rds_connection_alarm_threshold
  treat_missing_data  = "notBreaching"
  alarm_description   = "RDS connection count is approaching the HikariCP pool ceiling across all tasks"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.mysql.identifier
  }
}
