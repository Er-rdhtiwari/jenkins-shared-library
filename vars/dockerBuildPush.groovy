def call(String imageRepoName, String dockerfilePath, String contextDir, String awsRegion, String awsAccountId, String tag, String additionalTag = null) {
    def docker = new org.rdhcloudlab.Docker(this)
    return docker.buildAndPush(imageRepoName, dockerfilePath, contextDir, awsRegion, awsAccountId, tag, additionalTag)
}
