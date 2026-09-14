# SonarQube Quick Start

This is the short version for running the project with SonarQube, ngrok, GitHub, and branch protection.

## 1) Start SonarQube locally

```bash
docker compose -f docker-compose.sonar.yml up -d
docker ps
```

Open:

```text
http://localhost:9000
```

Login:

```text
admin / admin
```

Change the password and then create a project.

## 2) Create a Sonar token

Go to:

```text
My Account -> Security
```

Then:

- Generate Token
- Name it: `github-actions`
- Copy the token and save it safely

Important: do not commit it.

## 3) Expose Sonar with ngrok

Install ngrok and start a tunnel:

```bash
ngrok http 9000
```

This gives you a public URL like:

```text
https://xxxx.ngrok-free.app
```

Use that URL in GitHub Actions, not localhost.

## 4) Add GitHub secrets

In GitHub:

```text
Settings -> Secrets and variables -> Actions
```

Add:

```text
SONAR_HOST_URL = https://<your-ngrok-url>
SONAR_TOKEN = <your-sonarqube-token>
```

## 5) Push / PR to trigger the workflow

The workflow runs on:

- push to `main`
- pull request to `main`

File:

```text
.github/workflows/sonarqube.yml
```

The job name is:

```text
build-and-analyze
```

## 6) Protect the main branch

In GitHub:

```text
Settings -> Branches
```

Create branch protection for `main` and enable:

- Require a pull request before merging
- Require approvals: 1
- Require status checks to pass before merging
- Select: `build-and-analyze`

## 7) Final verification

- Push a change to a branch
- Open a PR
- Wait for the GitHub Action to run
- Confirm Sonar receives the analysis
- Confirm merge is blocked until approval and the Sonar check passes

## 8) If push is rejected

This usually means the repository has branch rules or a ruleset. Use:

```bash
git checkout -b feature/sonar-check
git push -u origin feature/sonar-check
```

Then open a PR to `main`.

## 9) Quick commands

```bash
docker compose -f docker-compose.sonar.yml up -d
git checkout -b feature/sonar-check
git add .
git commit -m "Add Sonar integration"
git push -u origin feature/sonar-check
```

## Important

- Localhost is only for local testing
- GitHub runners need a public URL
- Use ngrok for the exercise
- Never commit the Sonar token
