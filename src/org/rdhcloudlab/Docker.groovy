package org.rdhcloudlab

class Docker implements Serializable {
    Shell shell

    Docker(steps) {
        this.shell = new Shell(steps)
    }

    String buildAndPush(String imageRepoName, String dockerfilePath, String contextDir, String awsRegion, String awsAccountId, String tag, String additionalTag = null) {
        shell.requireNonEmpty("imageRepoName", imageRepoName)
        shell.requireNonEmpty("dockerfilePath", dockerfilePath)
        shell.requireNonEmpty("contextDir", contextDir)
        shell.requireNonEmpty("awsRegion", awsRegion)
        shell.requireNonEmpty("awsAccountId", awsAccountId)
        shell.requireNonEmpty("tag", tag)
        shell.requireFileExists("dockerfilePath", dockerfilePath)
        shell.requireDirExists("contextDir", contextDir)

        String localTag = "${imageRepoName}:${tag}"
        String fullImage = "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com/${imageRepoName}:${tag}"

        shell.info("Building Docker image ${localTag}")
        shell.run("docker build -f ${shell.singleQuote(dockerfilePath)} -t ${shell.singleQuote(localTag)} ${shell.singleQuote(contextDir)}")

        shell.info("Tagging Docker image as ${fullImage}")
        shell.run("docker tag ${shell.singleQuote(localTag)} ${shell.singleQuote(fullImage)}")

        shell.info("Pushing Docker image ${fullImage}")
        shell.run("docker push ${shell.singleQuote(fullImage)}")

        if (additionalTag != null && additionalTag.toString().trim()) {
            String additionalFull = "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com/${imageRepoName}:${additionalTag}"
            shell.info("Tagging Docker image as ${additionalFull}")
            shell.run("docker tag ${shell.singleQuote(localTag)} ${shell.singleQuote(additionalFull)}")
            shell.info("Pushing Docker image ${additionalFull}")
            shell.run("docker push ${shell.singleQuote(additionalFull)}")
        }

        return fullImage
    }
}
