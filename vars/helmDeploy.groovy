def call(String releaseName, String chartPath, String namespace, List valuesFilesList = [], Map setParamsMap = [:]) {
    def helm = new org.rdhcloudlab.Helm(this)
    helm.deploy(releaseName, chartPath, namespace, valuesFilesList, setParamsMap)
}

def rollback(String releaseName, int revision, String namespace) {
    def helm = new org.rdhcloudlab.Helm(this)
    helm.rollback(releaseName, revision, namespace)
}
