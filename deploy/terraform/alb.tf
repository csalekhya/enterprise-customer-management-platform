###############################################################################
# Application Load Balancer
#
# One listener, two target groups. Path-based routing sends /api/* and the health
# endpoints to the Spring Boot service and everything else to nginx, so the
# browser sees a single origin and the SPA never needs CORS.
###############################################################################

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Public ingress on 80/443 for the load balancer"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from the internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from the internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Egress is restricted to the VPC: the load balancer only ever talks to targets.
  egress {
    description = "To targets inside the VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = var.alb_deletion_protection
  enable_http2               = true
  # Longer than the API's slowest expected request but shorter than the client
  # timeout, so a stuck request fails visibly instead of hanging the browser.
  idle_timeout               = 60
  drop_invalid_header_fields = true

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

###############################################################################
# Target groups
###############################################################################

resource "aws_lb_target_group" "api" {
  name        = "${local.name_prefix}-api-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  # Readiness, not liveness: the readiness probe reports DOWN while Flyway is
  # still migrating, which keeps a new task out of rotation until it can serve.
  health_check {
    enabled             = true
    path                = "/actuator/health/readiness"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Long enough for in-flight requests to finish, short enough that a deploy does
  # not crawl.
  deregistration_delay = 30

  tags = {
    Name = "${local.name_prefix}-api-tg"
  }
}

resource "aws_lb_target_group" "web" {
  name        = "${local.name_prefix}-web-tg"
  port        = 80
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/healthz"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 3
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  deregistration_delay = 15

  tags = {
    Name = "${local.name_prefix}-web-tg"
  }
}

###############################################################################
# Listeners
###############################################################################

# With a certificate, 80 only redirects. Without one, it serves - which is fine
# for a throwaway demo environment and never for production.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = var.certificate_arn == "" ? "forward" : "redirect"

    target_group_arn = var.certificate_arn == "" ? aws_lb_target_group.web.arn : null

    dynamic "redirect" {
      for_each = var.certificate_arn == "" ? [] : [1]

      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = var.certificate_arn == "" ? 0 : 1

  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  # TLS 1.2 floor: 1.0 and 1.1 have no place in a new deployment.
  ssl_policy      = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
  }
}

###############################################################################
# Routing rules
###############################################################################

resource "aws_lb_listener_rule" "api_https" {
  count = var.certificate_arn == "" ? 0 : 1

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern {
      values = ["/api/*", "/v3/api-docs*", "/swagger-ui*", "/actuator/health*"]
    }
  }
}

resource "aws_lb_listener_rule" "api_http" {
  count = var.certificate_arn == "" ? 1 : 0

  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern {
      values = ["/api/*", "/v3/api-docs*", "/swagger-ui*", "/actuator/health*"]
    }
  }
}
