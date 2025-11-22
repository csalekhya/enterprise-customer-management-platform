###############################################################################
# Outputs
#
# Everything a deploy pipeline or an operator needs after `apply`. Secret values
# are exposed only as ARNs - the pipeline reads them from Secrets Manager, and a
# `terraform output` in a CI log never prints a credential.
###############################################################################

output "application_url" {
  description = "Public entry point for the SPA"
  value       = var.certificate_arn == "" ? "http://${aws_lb.main.dns_name}" : "https://${aws_lb.main.dns_name}"
}

output "api_base_url" {
  description = "Base URL of the REST API"
  value       = var.certificate_arn == "" ? "http://${aws_lb.main.dns_name}/api/v1" : "https://${aws_lb.main.dns_name}/api/v1"
}

output "swagger_url" {
  description = "OpenAPI explorer"
  value       = var.certificate_arn == "" ? "http://${aws_lb.main.dns_name}/swagger-ui.html" : "https://${aws_lb.main.dns_name}/swagger-ui.html"
}

output "alb_dns_name" {
  description = "Load balancer hostname, for a CNAME or an ALIAS record"
  value       = aws_lb.main.dns_name
}

output "alb_zone_id" {
  description = "Hosted zone of the load balancer, for a Route 53 ALIAS record"
  value       = aws_lb.main.zone_id
}

output "vpc_id" {
  description = "VPC identifier"
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "Subnets the ECS tasks run in"
  value       = aws_subnet.private[*].id
}

output "ecs_cluster_name" {
  description = "Cluster name, used by `aws ecs update-service` in the deploy job"
  value       = aws_ecs_cluster.main.name
}

output "ecs_api_service_name" {
  description = "API service name"
  value       = aws_ecs_service.api.name
}

output "ecs_web_service_name" {
  description = "Web service name"
  value       = aws_ecs_service.web.name
}

output "ecr_api_repository_url" {
  description = "Push target for the API image"
  value       = aws_ecr_repository.this["api"].repository_url
}

output "ecr_web_repository_url" {
  description = "Push target for the web image"
  value       = aws_ecr_repository.this["web"].repository_url
}

output "rds_endpoint" {
  description = "MySQL endpoint. Reachable only from inside the VPC"
  value       = aws_db_instance.mysql.address
}

output "rds_port" {
  description = "MySQL port"
  value       = aws_db_instance.mysql.port
}

output "database_secret_arn" {
  description = "Secrets Manager ARN holding the database credentials"
  value       = aws_secretsmanager_secret.db.arn
}

output "jwt_secret_arn" {
  description = "Secrets Manager ARN holding the JWT signing key"
  value       = aws_secretsmanager_secret.jwt.arn
}

output "cloudwatch_log_groups" {
  description = "Log groups for the two services"
  value = {
    api = aws_cloudwatch_log_group.api.name
    web = aws_cloudwatch_log_group.web.name
  }
}
