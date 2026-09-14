# SonarQube Quick Start

This is the short setup for local SonarQube, a GitHub Actions runner hosted in WSL2, pull-request analysis, and branch protection. It does not require ngrok or another public tunnel.

## 1. Start SonarQube

From PowerShell in the project root:

```powershell
docker compose -f docker-compose.sonar.yml up -d
(Invoke-RestMethod http://localhost:9000/api/system/status).status
```

Continue when the status is `UP`, then open `http://localhost:9000`. For a fresh installation, sign in with `admin / admin` and change the password.

## 2. Create the SonarQube token

Go to:

```text
My Account -> Security -> Generate Tokens
```

Create a user token named `github-actions-buy02` and copy it immediately. Never commit it.

## 3. Install the GitHub runner in WSL2

In the GitHub repository, open:

```text
Settings -> Actions -> Runners -> New self-hosted runner
```

Choose **Linux/x64**. In WSL, install the runner outside the project:

```bash
mkdir -p ~/actions-runner
cd ~/actions-runner
```

Run the commands displayed by GitHub. Use `buy02-wsl-runner` as the runner name and accept the default group, labels, and `_work` folder. Then start it:

```bash
./run.sh
```

Wait for `Listening for Jobs`.

## 4. Verify local connectivity from WSL

```bash
curl http://localhost:9000/api/system/status
```

If that fails, try `http://host.docker.internal:9000`. Use the address that returns `"status":"UP"`.

## 5. Add GitHub secrets

Go to:

```text
Settings -> Secrets and variables -> Actions
```

Add:

```text
SONAR_HOST_URL = http://localhost:9000
SONAR_TOKEN = <your SonarQube token>
```

Use `http://host.docker.internal:9000` instead if that was the address reachable from WSL.

## 6. Target the self-hosted runner

In `.github/workflows/sonarqube.yml`, configure:

```yaml
jobs:
  build-and-analyze:
    runs-on: [self-hosted, Linux, X64]
```

The workflow runs for pull requests targeting `main` and pushes to `main`.

## 7. Protect `main`

Under `Settings -> Branches`, create a protection rule for `main` and require:

- Pull requests before merging
- One teammate approval
- Passing status checks
- The `build-and-analyze` check
- Up-to-date branches before merging

If the check is not initially listed, run the workflow through the first PR and then add the check to the rule before merging.

## 8. Verify the setup

- Keep Docker Desktop, SonarQube, WSL, and `./run.sh` running.
- Push a non-protected branch and open a PR to `main`.
- Confirm the runner receives `build-and-analyze`.
- Confirm SonarQube receives the analysis.
- Confirm approval and the successful check are required before merge.

The runner connects outward to GitHub and reaches SonarQube locally, so no public tunnel is needed.
