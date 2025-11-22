###############################################################################
# Input variables
#
# Defaults are sized for a demo/staging footprint. Production overrides live in a
# tfvars file (see terraform.tfvars.example).
###############################################################################

variable "project_name" {
  description = "Short name used as a prefix for every resource"
  type        = string
  default     = "slate-crm"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,24}$", var.project_name))
    error_message = "project_name must be lower case alphanumeric with hyphens, 3-25 characters."
  }
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of dev, staging or prod."
  }
}

variable "owner" {
  description = "Tag value identifying the owner of these resources"
  type        = string
  default     = "crm-platform-team"
}

variable "aws_region" {
  description = "AWS region for every resource in this stack"
  type        = string
  default     = "us-east-1"
}

###############################################################################
# Networking
###############################################################################

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.40.0.0/16"
}

variable "availability_zone_count" {
  description = "Number of AZs to spread subnets across. Two is the minimum for an ALB and for RDS multi-AZ"
  type        = number
  default     = 2

  validation {
    condition     = var.availability_zone_count >= 2 && var.availability_zone_count <= 3
    error_message = "availability_zone_count must be 2 or 3."
  }
}

variable "single_nat_gateway" {
  description = "Use one NAT gateway instead of one per AZ. Cheaper, but the NAT's AZ becomes a single point of failure - keep false in prod"
  type        = bool
  default     = true
}

###############################################################################
# Load balancer / TLS
###############################################################################

variable "certificate_arn" {
  description = "ACM certificate ARN for the HTTPS listener. Empty means HTTP only, which is for local experimentation rather than a real deployment"
  type        = string
  default     = ""
}

variable "alb_deletion_protection" {
  description = "Prevent accidental deletion of the load balancer"
  type        = bool
  default     = false
}

###############################################################################
# ECS services
###############################################################################

variable "api_image_tag" {
  description = "Image tag deployed to the API service. CI sets this to the commit SHA"
  type        = string
  default     = "latest"
}

variable "web_image_tag" {
  description = "Image tag deployed to the web service"
  type        = string
  default     = "latest"
}

variable "api_cpu" {
  description = "Fargate CPU units for one API task"
  type        = number
  default     = 1024
}

variable "api_memory" {
  description = "Fargate memory (MiB) for one API task. The JVM is given 75% of this via MaxRAMPercentage"
  type        = number
  default     = 2048
}

variable "api_desired_count" {
  description = "Baseline number of API tasks"
  type        = number
  default     = 2
}

variable "api_min_capacity" {
  description = "Floor for API autoscaling. Two keeps the service available during a rolling deploy"
  type        = number
  default     = 2
}

variable "api_max_capacity" {
  description = "Ceiling for API autoscaling"
  type        = number
  default     = 8
}

variable "api_cpu_target" {
  description = "Target average CPU utilisation (%) for the API scaling policy"
  type        = number
  default     = 60
}

variable "web_cpu" {
  description = "Fargate CPU units for one web (nginx) task"
  type        = number
  default     = 256
}

variable "web_memory" {
  description = "Fargate memory (MiB) for one web task"
  type        = number
  default     = 512
}

variable "web_desired_count" {
  description = "Number of web tasks"
  type        = number
  default     = 2
}

###############################################################################
# Database
###############################################################################

variable "db_name" {
  description = "Initial schema name"
  type        = string
  default     = "crm"
}

variable "db_username" {
  description = "Master username. The password is generated and stored in Secrets Manager, never in state as plaintext input"
  type        = string
  default     = "crm_admin"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "db_allocated_storage" {
  description = "Initial storage (GiB)"
  type        = number
  default     = 50
}

variable "db_max_allocated_storage" {
  description = "Upper bound for storage autoscaling (GiB)"
  type        = number
  default     = 200
}

variable "db_multi_az" {
  description = "Provision a synchronous standby in a second AZ"
  type        = bool
  default     = true
}

variable "db_backup_retention_days" {
  description = "Automated backup retention window"
  type        = number
  default     = 7
}

variable "db_deletion_protection" {
  description = "Refuse to destroy the database"
  type        = bool
  default     = false
}

variable "rds_connection_alarm_threshold" {
  description = "DatabaseConnections value that triggers the saturation alarm. Keep it below api_max_capacity * DB_POOL_MAX"
  type        = number
  default     = 120
}

###############################################################################
# Observability
###############################################################################

variable "log_retention_days" {
  description = "CloudWatch log retention"
  type        = number
  default     = 30
}

variable "enable_container_insights" {
  description = "Enable ECS Container Insights (extra CloudWatch cost, much better task-level metrics)"
  type        = bool
  default     = true
}

###############################################################################
# Application configuration
###############################################################################

variable "cors_allowed_origins" {
  description = "Comma separated origin allowlist passed to the API. Same-origin in this topology, so this is only needed for an external console"
  type        = string
  default     = "https://crm.example.com"
}

variable "log_level_app" {
  description = "Log level for io.crmplatform"
  type        = string
  default     = "INFO"
}
