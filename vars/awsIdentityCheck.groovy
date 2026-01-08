def call() {
    def aws = new org.rdhcloudlab.Aws(this)
    return aws.identityCheck()
}
