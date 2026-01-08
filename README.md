# Jenkins Shared Library - rdh-lib

This repository provides a production-friendly Jenkins Shared Library for consistent CI/CD across:
- platform-infra (Terraform)
- platform-addons (Helm on EKS)
- ai-app (Build+Push to ECR + Deploy via Helm)

It includes strong logging, clear errors, input validation, and built-in validation helpers for AWS identity, EKS connectivity, Helm status, and rollout checks. No secrets are stored in code; use Jenkins credentials or environment variables.

## What is a Jenkins Shared Library
A Jenkins Shared Library is a reusable set of pipeline steps, classes, and resources that standardizes CI/CD workflows across multiple pipelines. It is loaded with `@Library('name') _` and exposes common functions from the `vars/` directory.

## Repository structure
```
jenkins-shared-library/
  README.md
  .gitignore
  vars/
    awsIdentityCheck.groovy
    awsEcrLogin.groovy
    dockerBuildPush.groovy
    kubeconfigUpdate.groovy
    helmDeploy.groovy
    terraformRun.groovy
    gateApprove.groovy
    validateTools.groovy
  src/org/rdhcloudlab/
    Shell.groovy
    Aws.groovy
    Docker.groovy
    Kubernetes.groovy
    Helm.groovy
  resources/
    templates/
      helm_set_params_example.yaml
      terraform_backend_example.hcl
```

## Jenkins configuration (Global Pipeline Libraries)
1) Manage Jenkins -> Configure System
2) Global Pipeline Libraries -> Add
3) Library name: `rdh-lib`
4) Default version: `main`
5) Retrieval method: Modern SCM
6) SCM: Git
7) Project repository: `https://git.example.com/rdh/jenkins-shared-library.git` (replace with your repo URL)
8) Save

## Library functions (vars)
- `validateTools(requiredList)`
  - Verifies binaries exist: aws, docker, terraform, kubectl, helm
  - Prints versions; fails with actionable errors if missing
- `awsIdentityCheck()`
  - Runs `aws sts get-caller-identity`
  - Fails if not authenticated
  - Prints account ID and ARN
- `awsEcrLogin(awsRegion, awsAccountId)`
  - Logs in to ECR using `aws ecr get-login-password | docker login`
- `dockerBuildPush(imageRepoName, dockerfilePath, contextDir, awsRegion, awsAccountId, tag, additionalTag = null)`
  - Builds, tags, pushes image to ECR
  - Returns the full image URI
- `kubeconfigUpdate(clusterName, awsRegion)`
  - Runs `aws eks update-kubeconfig`
  - Verifies with `kubectl cluster-info` and `kubectl get nodes`
- `helmDeploy(releaseName, chartPath, namespace, valuesFilesList, setParamsMap)`
  - Creates namespace if missing
  - `helm upgrade --install` with `--atomic --wait --timeout`
  - Shows `helm status`
  - Runs rollout checks for deployments in the release label scope
  - Includes `helmDeploy.rollback(releaseName, revision, namespace)`
- `terraformRun(envDir, action, extraVarsMap)`
  - `terraform init`, `fmt -check`, `validate`, `plan/apply/destroy`
  - Stores plan output for review as `tfplan*.txt`
  - Supports backend config already in `envDir/backend.tf`
  - Use `_requireApproval` and `_approvalMessage` in `extraVarsMap` to gate apply/destroy
- `gateApprove(message)`
  - Jenkins input step wrapper with clear prompt text

Note: Keys in `extraVarsMap` starting with `_` are reserved for library behavior (`_requireApproval`, `_approvalMessage`, `_planFile`).

## Example Jenkinsfile usage
### Minimal example
```groovy
@Library('rdh-lib') _

pipeline {
  agent any
  stages {
    stage('Validate Tools') {
      steps {
        validateTools(['aws', 'docker', 'terraform', 'kubectl', 'helm'])
      }
    }
    stage('AWS Identity') {
      steps {
        awsIdentityCheck()
      }
    }
  }
}
```

### platform-infra (Terraform)
```groovy
@Library('rdh-lib') _

pipeline {
  agent any
  environment {
    AWS_REGION = 'us-west-2'
    TF_ENV_DIR = 'envs/dev'
  }
  stages {
    stage('Validate Tools') {
      steps {
        validateTools(['aws', 'terraform'])
      }
    }
    stage('AWS Identity') {
      steps {
        awsIdentityCheck()
      }
    }
    stage('Terraform Plan') {
      steps {
        terraformRun(env.TF_ENV_DIR, 'plan', [environment: 'dev'])
      }
    }
    stage('Terraform Apply') {
      when {
        branch 'main'
      }
      steps {
        terraformRun(env.TF_ENV_DIR, 'apply', [
          environment: 'dev',
          _requireApproval: true,
          _approvalMessage: 'Approve terraform apply for dev'
        ])
      }
    }
  }
}
```

### platform-addons (Helm on EKS)
```groovy
@Library('rdh-lib') _

pipeline {
  agent any
  environment {
    AWS_REGION = 'us-west-2'
    CLUSTER_NAME = 'rdh-eks-dev'
    NAMESPACE = 'platform-addons'
  }
  stages {
    stage('Validate Tools') {
      steps {
        validateTools(['aws', 'kubectl', 'helm'])
      }
    }
    stage('AWS Identity') {
      steps {
        awsIdentityCheck()
      }
    }
    stage('Kubeconfig Update') {
      steps {
        kubeconfigUpdate(env.CLUSTER_NAME, env.AWS_REGION)
      }
    }
    stage('Helm Deploy') {
      steps {
        helmDeploy('platform-addons', 'charts/platform-addons', env.NAMESPACE, ['values/dev.yaml'], [
          'image.tag': '1.2.3',
          'replicaCount': 2
        ])
      }
    }
  }
}
```

### ai-app (Build + Push to ECR + Helm Deploy)
```groovy
@Library('rdh-lib') _

pipeline {
  agent any
  environment {
    AWS_REGION = 'us-west-2'
    AWS_ACCOUNT_ID = '123456789012'
    CLUSTER_NAME = 'rdh-eks-dev'
    NAMESPACE = 'ai-app'
    IMAGE_REPO = 'ai-app'
  }
  stages {
    stage('Validate Tools') {
      steps {
        validateTools(['aws', 'docker', 'kubectl', 'helm'])
      }
    }
    stage('AWS Identity') {
      steps {
        awsIdentityCheck()
      }
    }
    stage('ECR Login') {
      steps {
        awsEcrLogin(env.AWS_REGION, env.AWS_ACCOUNT_ID)
      }
    }
    stage('Build + Push Image') {
      steps {
        script {
          def imageUri = dockerBuildPush(
            env.IMAGE_REPO,
            'Dockerfile',
            '.',
            env.AWS_REGION,
            env.AWS_ACCOUNT_ID,
            env.BUILD_NUMBER,
            'dev-latest'
          )
          env.IMAGE_URI = imageUri
        }
      }
    }
    stage('Kubeconfig Update') {
      steps {
        kubeconfigUpdate(env.CLUSTER_NAME, env.AWS_REGION)
      }
    }
    stage('Helm Deploy') {
      steps {
        helmDeploy('ai-app', 'charts/ai-app', env.NAMESPACE, ['values/dev.yaml'], [
          'image.repository': "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com/${env.IMAGE_REPO}",
          'image.tag': env.BUILD_NUMBER
        ])
      }
    }
  }
}
```

## Troubleshooting
- Missing tools: `validateTools` fails with "not found in PATH".
  - Fix: Install the tool on the Jenkins agent and ensure PATH is configured.
- AWS identity check fails: `aws sts get-caller-identity` fails.
  - Fix: Configure AWS credentials (Jenkins credentials + environment variables) and verify IAM permissions.
- ECR login fails: `docker login` error.
  - Fix: Ensure IAM permissions include `ecr:GetAuthorizationToken` and the region/account are correct.
- kubeconfig update fails or cluster-info fails:
  - Fix: Validate EKS cluster name/region and that the agent can reach the EKS API endpoint.
- Helm deploy timeouts or rollout stuck:
  - Fix: Increase Helm timeout, inspect `kubectl get pods` and `kubectl describe` for failing resources, and check image pull permissions.

## Resources
- Example Helm set params: `resources/templates/helm_set_params_example.yaml`
- Example Terraform backend config: `resources/templates/terraform_backend_example.hcl`
