package org.rdhcloudlab

class Aws implements Serializable {
    Shell shell

    Aws(steps) {
        this.shell = new Shell(steps)
    }

    Map identityCheck() {
        shell.info("Checking AWS caller identity")
        try {
            String accountId = shell.runCapture("aws sts get-caller-identity --query Account --output text")
            String arn = shell.runCapture("aws sts get-caller-identity --query Arn --output text")

            if (!accountId || accountId == "None") {
                shell.fail("AWS account ID is empty. Verify AWS credentials on the Jenkins agent.")
            }
            if (!arn || arn == "None") {
                shell.fail("AWS ARN is empty. Verify AWS credentials on the Jenkins agent.")
            }

            shell.info("AWS account ID: ${accountId}")
            shell.info("AWS ARN: ${arn}")
            return [accountId: accountId, arn: arn]
        } catch (Exception ignored) {
            shell.fail("AWS authentication failed. Ensure AWS credentials are configured and have sts:GetCallerIdentity permission.")
        }
        return [:]
    }

    void ecrLogin(String awsRegion, String awsAccountId) {
        shell.requireNonEmpty("awsRegion", awsRegion)
        shell.requireNonEmpty("awsAccountId", awsAccountId)

        String registry = "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com"
        shell.info("Logging in to ECR registry ${registry}")
        shell.run("set -euo pipefail; aws ecr get-login-password --region ${shell.singleQuote(awsRegion)} | docker login --username AWS --password-stdin ${shell.singleQuote(registry)}")
    }

    void eksUpdateKubeconfig(String clusterName, String awsRegion) {
        shell.requireNonEmpty("clusterName", clusterName)
        shell.requireNonEmpty("awsRegion", awsRegion)

        shell.info("Updating kubeconfig for EKS cluster ${clusterName} in ${awsRegion}")
        shell.run("aws eks update-kubeconfig --name ${shell.singleQuote(clusterName)} --region ${shell.singleQuote(awsRegion)}")
    }
}
