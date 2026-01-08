def call(String clusterName, String awsRegion) {
    def aws = new org.rdhcloudlab.Aws(this)
    def kube = new org.rdhcloudlab.Kubernetes(this)

    aws.eksUpdateKubeconfig(clusterName, awsRegion)
    kube.clusterInfo()
    kube.getNodes()
}
