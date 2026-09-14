# SonarQube + GitHub Self-Hosted Runner Setup Instructions

This guide explains how to run SonarQube locally and analyze pull requests with a GitHub Actions runner hosted in WSL2. The runner can reach SonarQube through the local machine, so SonarQube does not need to be exposed publicly through ngrok or another tunnel.

This setup is intended for a trusted team working on a private or controlled school-project repository. A self-hosted runner executes workflow code on the host computer, so do not allow untrusted pull requests to use it.

## 1. Prerequisites

You need:

- Docker Desktop installed and running
- WSL2 with a Linux distribution such as Ubuntu
- Git installed
- A GitHub repository and permission to change its settings
- The project checked out locally
- Only trusted collaborators allowed to create or modify workflows

The workflow uses Java 21 and Node.js 22. The GitHub setup actions install these versions on the runner.

Confirm that the Linux distribution uses WSL2:

```powershell
wsl --list --verbose
```

## 2. Start SonarQube locally

From the project root in PowerShell, run:

```powershell
docker compose -f docker-compose.sonar.yml up -d
docker compose -f docker-compose.sonar.yml ps
```

Open:

```text
http://localhost:9000
```

Check the server status:

```powershell
(Invoke-RestMethod http://localhost:9000/api/system/status).status
```

Continue when it returns `UP` rather than `STARTING`.

## 3. Configure SonarQube

### 3.1 Log in

The default credentials for a fresh installation are:

```text
Username: admin
Password: admin
```

Change the default password when prompted.

### 3.2 Create the project

In the SonarQube UI, select **Create project**, choose the manual setup method, and assign a clear project name and key.

### 3.3 Create a token

Go to:

```text
My Account -> Security -> Generate Tokens
```

Create a user token named `github-actions-buy02` and copy it immediately. Never commit this token or place it directly in the workflow file.

## 4. Install a self-hosted GitHub Actions runner in WSL2

Keep the runner outside the project repository. In an Ubuntu/WSL terminal, create its directory:

```bash
mkdir -p ~/actions-runner
cd ~/actions-runner
```

In the GitHub repository, go to:

```text
Settings -> Actions -> Runners -> New self-hosted runner
```

Select **Linux** and **x64**, then run the download and configuration commands GitHub displays. The registration token in the generated command is temporary; do not publish or reuse it.

Recommended answers during configuration:

```text
Runner group: Default
Runner name: buy02-wsl-runner
Additional labels: press Enter to skip
Work folder: press Enter to use _work
```

Start the runner:

```bash
cd ~/actions-runner
./run.sh
```

Keep this terminal open. The runner is ready when it displays `Listening for Jobs`.

## 5. Verify that WSL can reach SonarQube

Run this from the WSL terminal, not PowerShell:

```bash
curl http://localhost:9000/api/system/status
```

If it returns a JSON response with `"status":"UP"`, use `http://localhost:9000` as the Sonar host. If `localhost` is not reachable from the installed WSL configuration, test:

```bash
curl http://host.docker.internal:9000/api/system/status
```

Use the address that returns `UP`.

## 6. Configure GitHub secrets

In the GitHub repository, go to:

```text
Settings -> Secrets and variables -> Actions
```

Add these repository secrets:

```text
SONAR_HOST_URL = http://localhost:9000
SONAR_TOKEN = <the token generated in SonarQube>
```

If only `host.docker.internal` worked from WSL, use `http://host.docker.internal:9000` for `SONAR_HOST_URL`. Secrets are configured in GitHub and must not be committed to Git.

## 7. Configure the workflow for the self-hosted runner

The workflow is located at:

```text
.github/workflows/sonarqube.yml
```

Its job must target the automatically assigned Linux runner labels:

```yaml
jobs:
  build-and-analyze:
    runs-on: [self-hosted, Linux, X64]
```

The workflow runs on pushes to `main` and pull requests targeting `main`. It performs backend Sonar analysis, the frontend build and lint checks, and frontend Sonar analysis.

## 8. Protect the main branch

Go to:

```text
Settings -> Branches
```

Create a protection rule for `main` and enable:

- Require a pull request before merging
- Require one approval
- Dismiss stale approvals when new reviewable commits are pushed
- Require status checks to pass before merging
- Require branches to be up to date before merging
- Require conversation resolution before merging

Select `build-and-analyze` as a required status check. If it is not listed, save the other protections, open the first pull request, and let the workflow run once. Then return to the rule and select the newly registered `build-and-analyze` check before merging.

## 9. Verify the pull-request workflow

Create and push a feature or configuration branch, then open a pull request targeting `main`:

```bash
git checkout -b chore/self-hosted-runner
git add .github/workflows/sonarqube.yml instructions.md SONAR_QUICKSTART.md
git commit -m "Configure Sonar analysis on self-hosted runner"
git push -u github chore/self-hosted-runner
```

Verify that:

- SonarQube is `UP`.
- `./run.sh` displays `Listening for Jobs` in WSL.
- Opening the PR starts `build-and-analyze`.
- The WSL runner receives and completes the job.
- SonarQube receives the backend and frontend analysis.
- The PR cannot merge until the check passes and a teammate approves it.

## 10. Operating the local runner

SonarQube, Docker Desktop, WSL, and the runner must remain active while a workflow is executing. If the runner is offline, GitHub leaves the job queued until it returns.

Start the runner when it is needed:

```bash
cd ~/actions-runner
./run.sh
```

Run the analysis scripts manually from WSL when troubleshooting:

```bash
bash scripts/run-sonar-backend.sh
bash scripts/run-sonar-frontend.sh
```

The self-hosted runner initiates an outbound connection to GitHub and accesses SonarQube locally. No public tunnel, inbound router port, or ngrok account is required.
