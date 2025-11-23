# Contributing

Pull requests are welcome. This page covers the local setup, the rules the codebase relies on,
and the checks CI will run on your change.

## Getting set up

```bash
cp .env.example .env
docker compose up --build        # full stack on :3000 and :8080
```

For a tighter loop, run the API and the web app directly:

```bash
cd backend  && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

## Ground rules

- **Migrations are append-only.** Never edit a `V*.sql` file that has been merged; add
  `V{n+1}__description.sql` instead. Hibernate boots with `ddl-auto: validate`, so the entity
  mapping and the migration must agree exactly (column type *and* length).
- **Authorization is enforced twice.** `@PreAuthorize` on the handler for the role, and an
  ownership check in the service for the row. Hiding a button in the UI is not a control.
- **New endpoints need a test.** A service-level unit test for the rule, and an entry in the
  controller integration test if the contract changed.
- **Keep DTOs and entities separate.** Entities never leave the service layer.

## Before opening a PR

```bash
cd backend  && mvn -B verify
cd frontend && npm run lint && npm run test && npm run build
terraform -chdir=deploy/terraform fmt -check && terraform -chdir=deploy/terraform validate
```

CI runs the same four jobs. Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
(`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).

## Reporting a security issue

Please use GitHub's private vulnerability reporting (**Security → Report a vulnerability**) rather
than opening a public issue.
