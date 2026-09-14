# SonarQube + GitHub + ngrok Setup Instructions

This guide explains how to run the project with SonarQube locally, expose it publicly through ngrok so GitHub-hosted runners can reach it, connect it to GitHub Actions, and enforce the required branch protection checks.

This setup is intended for a local exercise and lab environment. It is suitable for a school project or a demo, not for a production-grade public deployment.

## 1. Prerequisites

You need:

- Docker Desktop installed and running
- Git installed
- A GitHub repository
- A GitHub account with permission to change repo settings
- A SonarQube instance
- An ngrok account with an authtoken
- The project checked out locally

Required software versions:

- Docker Compose v2
- Java 21 for the backend
- Node.js 22 for the frontend

## 2. Start SonarQube locally with Docker

From the project root, run:

```bash
docker compose -f docker-compose.sonar.yml up -d
```

This starts:

- SonarQube on port 9000
- PostgreSQL on port 5432

Check that the containers are running:

```bash
docker ps
```

Open:

```text
http://localhost:9000
```

## 3. Configure SonarQube

### 3.1 log in

Default login for a fresh SonarQube install:

```text
Username: admin
Password: admin
```

You should immediately change the password.

### 3.2 create a project

In the SonarQube UI:

- Click Create project
- Or go to Projects → Create project
- Choose the project setup method
- Give the project a name and key

### 3.3 create a global token

Go to:

```text
My Account -> Security
```

Then:

- click Generate Token
- give it a clear name, for example: `github-actions`
- copy the token immediately and save it in a secure place

Important:

- Never commit the Sonar token to the repository
- Store it in GitHub repository secrets or a local `.env` file that is gitignored

## 4. Expose SonarQube using ngrok

GitHub-hosted runners cannot access localhost. To make the Sonar server reachable from GitHub Actions, expose the local port via a public tunnel.

### 4.1 install ngrok

Download the latest stable ngrok binary from the official site:

```text
https://dashboard.ngrok.com/get-started/setup
```

If you use the authtoken, install it with:

```bash
ngrok config add-authtoken <your-ngrok-authtoken>
```

If the ngrok CLI is too old for your account, you will see an error like:

```text
Your ngrok-agent version is too old
```

In that case, upgrade to a newer version. Do not use an old 3.3.x binary if your account requires a newer version.

### 4.2 start the tunnel

Run:

```bash
ngrok http 9000
```

This will produce a public URL similar to:

```text
https://buffoon-despite-catalog.ngrok-free.dev
```

Use this URL as the Sonar host for GitHub Actions.

Important:

- The free ngrok tier can show a browser warning page before the website loads.
- This is normal for the free plan.
- GitHub Actions is not a browser, so it can still call the API directly.
- For a production-like or long-term setup, use a paid ngrok plan, a self-hosted VM, or a publicly reachable server.

## 5. Configure GitHub secrets

In the GitHub repository:

```text
Settings -> Secrets and variables -> Actions
```

Add these repository secrets:

```text
SONAR_HOST_URL = https://<your-ngrok-url>
SONAR_TOKEN = <your-sonarqube-token>
```

Example:

```text
SONAR_HOST_URL = https://buffoon-despite-catalog.ngrok-free.dev
SONAR_TOKEN = squ_1234567890abcdef
```

Do not use localhost in this value.

## 6. Confirm the GitHub Actions workflow

The repository includes a workflow at:

```text
.github/workflows/sonarqube.yml
```

This workflow triggers on:

- push to `main`
- pull requests targeting `main`

It runs:

- backend Sonar analysis
- frontend build and lint
- frontend Sonar analysis

Check the workflow file and ensure it references the correct scripts and environment variables.

## 7. Make the Sonar status check required in GitHub

Go to:

```text
GitHub -> Settings -> Branches
```

Add a branch protection rule for `main`.

Enable:

- Require a pull request before merging
- Require approvals: `1`
- Require status checks to pass before merging
- Select the required check: `build-and-analyze`
- Optionally: Require branches to be up to date before merging

This ensures that a PR cannot be merged unless:

- it has at least one approval
- the GitHub Actions Sonar job succeeds

## 8. Workflow status check name

The status check name is the job name in the workflow file.

In this repo the job is:

```yaml
jobs:
  build-and-analyze:
```

So in GitHub branch protection, you must select:

```text
build-and-analyze
```

If the workflow has not run yet, the check will not appear in the list until the first push or PR run completes.

## 9. Push a change and verify the pipeline

Create a small change and push it to a branch.

Example:

```bash
git checkout -b fix/sonar-setup
git add .
git commit -m "Add Sonar config validation"
git push -u origin fix/sonar-setup
```

Then open a pull request to `main`.

Check:

- the workflow starts
- the job `build-and-analyze` runs
- Sonar receives the scan results
- the GitHub status check shows success or failure
- the PR cannot merge until approval and the required status check pass

## 10. Local validation commands

Run these on your machine for a quick local validation:

```bash
docker compose -f docker-compose.sonar.yml up -d
docker ps
```

Then browse:

```text
http://localhost:9000
```

For ngrok:

```bash
ngrok http 9000
```

For manual backend checks:

```bash
bash scripts/run-sonar-backend.sh
```

For manual frontend checks:

```bash
bash scripts/run-sonar-frontend.sh
```

## 11. If the push is rejected by GitHub

If `git push` fails, most likely the issue is branch protection or a ruleset on the repository.

Common causes:

- pushing directly to a protected branch
- required status checks not passing
- PR approval not granted
- ruleset disallowing direct pushes

Solution:

- use a feature branch
- open a pull request
- wait for required checks to pass
- get approval and merge

Example:

```bash
git checkout -b feature/sonar-check
git push -u origin feature/sonar-check
```

Then create a PR to `main`.

## 12. Important notes

- Use `localhost` only for local development.
- Use the ngrok public URL for GitHub-hosted runners.
- Never store the Sonar token in GitHub code files.
- Keep the token only in GitHub repository secrets or a local `.env` file that is ignored by Git.
- The free ngrok plan is okay for a lab exercise, but a paid or self-hosted public host is recommended for production-like setups.

## 13. Summary

To complete the exercise you need all of the following:

1. SonarQube running locally via Docker
2. A Sonar admin password change
3. A valid Sonar token generated from My Account → Security
4. A public hosted URL from ngrok pointing to port 9000
5. GitHub repository secrets:
   - `SONAR_HOST_URL`
   - `SONAR_TOKEN`
6. A GitHub Actions workflow that triggers on push/PR
7. A required GitHub status check for `build-and-analyze`
8. Required PR approval before merging to main
9. Successful workflow run proving the repo is integrated with SonarQube

Once these are in place, the project is considered properly integrated with SonarQube and GitHub for this exercise.

## 14. Audit note: SonarQube issue resolved

To demonstrate a code-quality improvement required by the audit, a SonarQube issue was resolved in:

```text
api-gateway/src/main/java/com/example/apigateway/security/SecurityConfig.java
```

SonarQube reported duplicated string literals for the `SELLER` role and the `/products/{id}` path. These literals were replaced with the following class constants:

```java
private static final String PRODUCT_BY_ID_PATH = "/products/{id}";
private static final String SELLER_ROLE = "SELLER";
```

This change removes the duplication, improves maintainability, and provides evidence that an issue identified by SonarQube was reviewed and fixed. After pushing the change, run the GitHub Actions workflow again and confirm in SonarQube that the issues are marked as resolved.
