def call(String awsRegion, String awsAccountId) {
    def aws = new org.rdhcloudlab.Aws(this)
    aws.ecrLogin(awsRegion, awsAccountId)
}
