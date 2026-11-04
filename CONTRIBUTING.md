# Contributing

## Workflow

1. Pick an issue in **Ready to pick up** on the [Sprint1 board](https://github.com/users/LPuehringerStudent/projects/14),
   assign yourself and move it to **In Progress**.
2. Create a branch from `main`:
   `<type>/<issue>-<short-name>`, for example `feat/22-connect-webuntis` or `fix/41-leave-by-dst`.
   Types: `feat`, `fix`, `docs`, `infra`, `test`, `refactor`, `spike`.
3. Commit in small steps. Message style: `feat(backend): add saved route endpoint (#22)`.
4. Open a pull request against `main`, move the issue to **In Review**, and write in the description:
   what changed, how you tested it, and `Closes #<issue>` (or `Part of #<issue>` if work remains).
5. Merge after CI is green and the review is done. Use **squash merge** and delete the branch.

## Reviews (M03)

- Every pull request needs the approval of the project lead (`.github/CODEOWNERS`).
- Teammates are welcome to review too; answer every review comment before merging, or in a follow-up PR.
- **Stacked PRs:** if your PR builds on another open PR, say so at the top. Merge the base first, then rebase
  yours onto `main`; deleting the base branch closes PRs that still target it.

## CI

`.github/workflows/ci.yml` runs on every push and pull request:

- **backend:** `mvn verify` (tests need Docker for a temporary PostgreSQL), a check that
  `docs/api/openapi.yaml` is up to date, and a start of the API against an empty database.
- **frontend:** `ng test` and `ng build`.
- **authors** (`check-authors.yml`): see below.

Run the same locally before pushing:

```sh
mvn -f backend/pom.xml verify
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
```

## Commit authors

`check-authors.yml` fails if a commit's author or committer is not one of the team's GitHub accounts
(`LPuehringerStudent`, `ayan2310`, `BehrangEl11`, `David-Fruehwirt`), or if author, committer or
message names a bot or AI assistant. Set your Git email to one linked to your GitHub account:

```sh
git config user.email "<your GitHub email>"
```

## Rules from the specification

- Never log WebUntis keys, passwords or private school data (S01).
- Every private query goes through `CurrentStudent` (S02). See `backend/.../security/CurrentStudent.java`.
- No columns for passwords, home addresses, GPS, absence reasons or notes (L02).
- Database changes only through new Flyway migrations in `backend/src/main/resources/db/migration`;
  never edit a migration that is already on `main`.
- Times are stored as `timestamptz` in UTC (5.4).
