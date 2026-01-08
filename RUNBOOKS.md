# Runbooks for rdh-lib

This document contains three runbooks to help you operate and validate the Jenkins Shared Library in a permanent Jenkins server environment. Each runbook includes Jenkins Admin setup steps, CLI validation checkpoints, and common guardrails.

## Runbook 1: Library installation and baseline validation

Purpose: Install the shared library, validate agent tooling, and confirm AWS identity before any pipeline runs.

Jenkins Admin steps (one-time):
1) Manage Jenkins -> Configure System -> Global Pipeline Libraries -> Add
2) Library name: `rdh-lib`
3) Default version: `main` (or a fixed tag for stability)
4) Retrieval method: Modern SCM -> Git
5) Repository URL: your `jenkins-shared-library` Git URL
6) Save
7) Ensure required plugins are installed: Pipeline, Git, Credentials, Credentials Binding
8) Configure credentials:
   - Prefer AWS IAM role for the Jenkins agent (no static keys)
   - If using keys, store them in Jenkins Credentials and map to env vars in the job

CLI validation steps on a Jenkins agent (before running pipelines):
1) Confirm tools are present:
   - `aws --version`
   - `docker --version`
   - `terraform version`
   - `kubectl version --client --short`
   - `helm version --short`
2) Confirm AWS identity is available:
   - `aws sts get-caller-identity`
   - Expected: JSON with Account and Arn
3) Confirm Docker can talk to the daemon:
   - `docker ps`

Pipeline validation checkpoints:
1) Add this stage at the start of every pipeline:
   - `validateTools(['aws','docker','terraform','kubectl','helm'])`
2) Add AWS identity validation before any AWS/EKS/ECR step:
   - `awsIdentityCheck()`

Why this helps a permanent Jenkins server:
- Ensures every agent has baseline tooling before executing CI/CD steps.
- Centralizes validation and errors so failures are clear and consistent.
- Reduces drift between pipelines because every job calls the same checks.

Common issues and fixes:
- Tool missing: install on the agent and re-run `validateTools`.
- AWS identity fails: confirm credential binding or IAM role for the agent.

---

## Runbook 2: EKS and Helm deployment workflow

Purpose: Validate EKS connectivity and deploy Helm charts with rollout checks and rollback.

Jenkins Admin steps:
1) Ensure Jenkins agent can reach the EKS API endpoint.
2) Ensure AWS IAM role/user is mapped in `aws-auth` ConfigMap for EKS.
3) Confirm Helm chart repository access if charts are not in the repo.

CLI validation steps on a Jenkins agent:
1) Update kubeconfig:
   - `aws eks update-kubeconfig --name <cluster> --region <region>`
2) Validate cluster access:
   - `kubectl cluster-info`
   - `kubectl get nodes`
3) Validate Helm:
   - `helm version --short`

Pipeline validation checkpoints:
1) `kubeconfigUpdate(clusterName, awsRegion)`
   - Runs `aws eks update-kubeconfig`
   - Validates `kubectl cluster-info` and `kubectl get nodes`
2) `helmDeploy(releaseName, chartPath, namespace, valuesFilesList, setParamsMap)`
   - Creates namespace if missing
   - Runs Helm upgrade/install with `--atomic` and `--wait`
   - Runs rollout checks for deployments
3) Roll back if needed:
   - `helmDeploy.rollback(releaseName, revision, namespace)`

Why this helps a permanent Jenkins server:
- Standardizes EKS access validation for every deployment.
- Captures Helm status and rollout feedback in Jenkins logs.
- Makes rollback a first-class operation with a consistent interface.

Common issues and fixes:
- `kubectl` access denied: update `aws-auth` mapping for the Jenkins role/user.
- Helm timeout: increase timeout or check pod status (`kubectl get pods -n <ns>`).

---

## Runbook 3: Terraform plan/apply/destroy workflow

Purpose: Manage Terraform workflows with consistent init/validate/plan steps and gated apply/destroy.

Jenkins Admin steps:
1) Ensure Terraform is installed on all build agents.
2) Ensure remote backend (S3 + DynamoDB) is configured in `backend.tf`.
3) Enforce approval gates for production jobs.

CLI validation steps on a Jenkins agent:
1) Validate Terraform:
   - `terraform version`
2) Validate state backend access (from the env directory):
   - `terraform init -input=false`
   - `terraform validate`

Pipeline validation checkpoints:
1) Plan:
   - `terraformRun(envDir, 'plan', [environment: 'dev'])`
   - Produces `tfplan` and `tfplan.txt` for review
2) Apply with approval gate:
   - `terraformRun(envDir, 'apply', [
        environment: 'prod',
        _requireApproval: true,
        _approvalMessage: 'Approve terraform apply for prod'
      ])`
3) Destroy with approval gate:
   - `terraformRun(envDir, 'destroy', [
        environment: 'prod',
        _requireApproval: true,
        _approvalMessage: 'Approve terraform destroy for prod'
      ])`

Why this helps a permanent Jenkins server:
- Ensures `init`, `fmt -check`, and `validate` always run before changes.
- Provides consistent plan artifacts and gated apply/destroy for auditability.
- Reduces error-prone ad hoc Terraform usage across pipelines.

Common issues and fixes:
- Backend init failure: verify S3 bucket and DynamoDB lock table permissions.
- Plan/apply error due to missing vars: set required vars in the pipeline or `extraVarsMap`.

---

## Important operational notes
- Prefer pinning the library to a tag in production to avoid surprise changes.
- Never store secrets in the repo. Use Jenkins credentials and environment variables.
- Review IAM permissions for least privilege per pipeline (ECR, EKS, Terraform backend).
- Keep Jenkins agents patched and upgrade core tools (awscli, kubectl, helm, terraform) regularly.
