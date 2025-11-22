###############################################################################
# ECS Fargate services
#
# Two services in the private subnets:
#   api - Spring Boot, secrets injected at start-up, autoscaled on CPU
#   web - nginx serving the built SPA and proxying /api to the ALB
#
# The web tier proxies through the load balancer rather than talking to the API
# tasks directly: one routing table, one place to read access logs, and no
# service-discovery dependency between the two services.
###############################################################################

resource "aws_security_group" "api" {
  name        = "${local.name_prefix}-api-sg"
  description = "API tasks: ingress from the ALB only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTP from the load balancer"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Outbound is open: the task needs MySQL, Secrets Manager, ECR and CloudWatch.
  # Narrowing this to prefix lists is the next hardening step, not a shortcut.
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-api-sg"
  }
}

resource "aws_security_group" "web" {
  name        = "${local.name_prefix}-web-sg"
  description = "Web tasks: ingress from the ALB only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTP from the load balancer"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-web-sg"
  }
}

###############################################################################
# API task definition
###############################################################################

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64" # Graviton: the JVM runs well on it and it is cheaper per vCPU
  }

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = "${aws_ecr_repository.this["api"].repository_url}:${var.api_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "docker" },
        { name = "DB_HOST", value = aws_db_instance.mysql.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.mysql.port) },
        { name = "DB_NAME", value = var.db_name },
        { name = "DB_USE_SSL", value = "true" },
        { name = "DB_POOL_MAX", value = "15" },
        { name = "JWT_ISSUER", value = local.name_prefix },
        { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
        { name = "LOG_LEVEL_APP", value = var.log_level_app },
      ]

      # Pulled by the ECS agent from Secrets Manager at start-up. `secrets` keeps
      # the values out of the task definition JSON, which is world-readable to
      # anyone with ecs:DescribeTaskDefinition.
      secrets = [
        {
          name      = "DB_USERNAME"
          valueFrom = "${aws_secretsmanager_secret.db.arn}:username::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.db.arn}:password::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = aws_secretsmanager_secret.jwt.arn
        },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "api"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health/readiness || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90 # JVM start-up plus Flyway on a cold database
      }

      stopTimeout = 30 # graceful shutdown: Spring drains in-flight requests
    }
  ])

  tags = {
    Name = "${local.name_prefix}-api-task"
  }
}

resource "aws_ecs_service" "api" {
  name            = "${local.name_prefix}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"

  enable_execute_command = true

  # Rolling deploy with room for one extra task and no dip below the current
  # count, so a deploy never reduces capacity.
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  deployment_circuit_breaker {
    enable   = true
    rollback = true # a task that never passes its health check rolls the deploy back automatically
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  health_check_grace_period_seconds = 120

  lifecycle {
    # CI updates the image tag out of band; ignoring desired_count lets the
    # autoscaler own it without fighting Terraform.
    ignore_changes = [desired_count]
  }

  depends_on = [
    aws_lb_listener.http,
    aws_iam_role_policy.ecs_task_execution_secrets,
  ]

  tags = {
    Name = "${local.name_prefix}-api-service"
  }
}

###############################################################################
# Web task definition
###############################################################################

resource "aws_ecs_task_definition" "web" {
  family                   = "${local.name_prefix}-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.web_cpu
  memory                   = var.web_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = "web"
      image     = "${aws_ecr_repository.this["web"].repository_url}:${var.web_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 80
          protocol      = "tcp"
        }
      ]

      environment = [
        # nginx proxies /api back through the load balancer, which then routes it
        # to the API target group.
        { name = "API_UPSTREAM", value = "${aws_lb.main.dns_name}:80" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.web.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "web"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -q --spider http://localhost/healthz || exit 1"]
        interval    = 30
        timeout     = 3
        retries     = 3
        startPeriod = 15
      }
    }
  ])

  tags = {
    Name = "${local.name_prefix}-web-task"
  }
}

resource "aws_ecs_service" "web" {
  name            = "${local.name_prefix}-web"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = var.web_desired_count

  # The web tier is stateless static content, so most of it can ride Spot for a
  # ~70% saving, with one on-demand task as a floor.
  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 3
  }

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.web.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = 80
  }

  health_check_grace_period_seconds = 30

  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${local.name_prefix}-web-service"
  }
}

###############################################################################
# Autoscaling
#
# CPU target tracking on the API. The API is IO bound, so CPU rises with request
# concurrency in a way that tracks load well, and target tracking scales out
# quickly while scaling in slowly - which is the right asymmetry when the cost of
# being under-provisioned is a timeout and the cost of being over-provisioned is
# a few cents.
###############################################################################

resource "aws_appautoscaling_target" "api" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.api_min_capacity
  max_capacity       = var.api_max_capacity
}

resource "aws_appautoscaling_policy" "api_cpu" {
  name               = "${local.name_prefix}-api-cpu-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = var.api_cpu_target

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    scale_out_cooldown = 60  # react fast to a spike
    scale_in_cooldown  = 300 # shed capacity slowly to avoid flapping
  }
}

# A second policy on request count per target catches the case where requests
# queue on IO without CPU rising - a slow database, typically.
resource "aws_appautoscaling_policy" "api_requests" {
  name               = "${local.name_prefix}-api-request-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = 1000 # requests per target per minute

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.main.arn_suffix}/${aws_lb_target_group.api.arn_suffix}"
    }

    scale_out_cooldown = 60
    scale_in_cooldown  = 300
  }
}
