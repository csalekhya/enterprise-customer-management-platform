###############################################################################
# RDS MySQL
#
# Multi-AZ, in the isolated database subnets, reachable only from the API task
# security group. Parameters are tuned to match what the application assumes:
# UTC, utf8mb4 and a slow-query log that actually catches something.
###############################################################################

resource "aws_db_subnet_group" "main" {
  name        = "${local.name_prefix}-db-subnets"
  description = "Isolated database subnets"
  subnet_ids  = aws_subnet.database[*].id

  tags = {
    Name = "${local.name_prefix}-db-subnets"
  }
}

resource "aws_security_group" "database" {
  name        = "${local.name_prefix}-db-sg"
  description = "MySQL from the API tasks only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-db-sg"
  }
}

# Declared as a standalone rule rather than inline so that the API security group
# can reference the database group without a cycle.
resource "aws_vpc_security_group_ingress_rule" "database_from_api" {
  security_group_id            = aws_security_group.database.id
  description                  = "MySQL from the API service"
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.api.id
}

resource "aws_db_parameter_group" "mysql" {
  name        = "${local.name_prefix}-mysql80"
  family      = "mysql8.0"
  description = "CRM platform MySQL 8.0 parameters"

  parameter {
    name  = "time_zone"
    value = "UTC"
  }

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }

  # Anything slower than 500 ms on this schema means a missing index, not a big
  # result set - every list endpoint is paged and every dashboard number is an
  # aggregate.
  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  parameter {
    name  = "long_query_time"
    value = "0.5"
  }

  # Surfaces full table scans in the slow log even when they happen to be fast on
  # a small dataset, which is exactly when a missing index is cheap to fix.
  parameter {
    name  = "log_queries_not_using_indexes"
    value = "1"
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${local.name_prefix}-mysql-params"
  }
}

resource "aws_db_instance" "mysql" {
  identifier = "${local.name_prefix}-mysql"

  engine         = "mysql"
  engine_version = "8.0.39"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result
  port     = 3306

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.mysql.name
  publicly_accessible    = false

  multi_az                  = var.db_multi_az
  backup_retention_period   = var.db_backup_retention_days
  backup_window             = "03:00-04:00"
  maintenance_window        = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot     = true
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${local.name_prefix}-final-${formatdate("YYYYMMDDhhmm", timestamp())}" : null

  # Minor versions are applied in the maintenance window; a major upgrade is a
  # deliberate, tested change.
  auto_minor_version_upgrade = true
  apply_immediately          = var.environment != "prod"

  enabled_cloudwatch_logs_exports       = ["error", "slowquery"]
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  monitoring_interval                   = 60
  monitoring_role_arn                   = aws_iam_role.rds_monitoring.arn

  tags = {
    Name = "${local.name_prefix}-mysql"
  }

  lifecycle {
    # A generated final-snapshot name changes on every plan; ignoring it keeps
    # the plan clean.
    ignore_changes = [final_snapshot_identifier]
  }
}

###############################################################################
# Enhanced monitoring role
###############################################################################

data "aws_iam_policy_document" "rds_monitoring_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name               = "${local.name_prefix}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.rds_monitoring_assume.json

  tags = {
    Name = "${local.name_prefix}-rds-monitoring"
  }
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
