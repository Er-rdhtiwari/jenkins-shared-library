package org.rdhcloudlab

class Kubernetes implements Serializable {
    Shell shell

    Kubernetes(steps) {
        this.shell = new Shell(steps)
    }

    void clusterInfo() {
        shell.info("Validating Kubernetes cluster connectivity")
        shell.run("kubectl cluster-info")
    }

    void getNodes() {
        shell.info("Fetching Kubernetes nodes")
        shell.run("kubectl get nodes")
    }

    void ensureNamespace(String namespace) {
        shell.requireNonEmpty("namespace", namespace)
        int status = shell.runStatus("kubectl get namespace ${shell.singleQuote(namespace)} >/dev/null 2>&1")
        if (status != 0) {
            shell.info("Creating namespace ${namespace}")
            shell.run("kubectl create namespace ${shell.singleQuote(namespace)}")
        } else {
            shell.info("Namespace ${namespace} already exists")
        }
    }

    List<String> deploymentsForRelease(String releaseName, String namespace) {
        shell.requireNonEmpty("releaseName", releaseName)
        shell.requireNonEmpty("namespace", namespace)

        String selector = "app.kubernetes.io/instance=${releaseName}"
        String output = shell.runCapture("kubectl get deployment -n ${shell.singleQuote(namespace)} -l ${shell.singleQuote(selector)} -o name 2>/dev/null || true")
        if (!output) {
            String legacySelector = "release=${releaseName}"
            output = shell.runCapture("kubectl get deployment -n ${shell.singleQuote(namespace)} -l ${shell.singleQuote(legacySelector)} -o name 2>/dev/null || true")
        }
        if (!output) {
            return []
        }
        return output.split("\n").collect { it.trim() }.findAll { it }
    }

    void rolloutStatus(List<String> deployments, String namespace) {
        shell.requireNonEmpty("namespace", namespace)
        if (deployments == null || deployments.isEmpty()) {
            shell.warn("No deployments found for rollout checks in namespace ${namespace}")
            return
        }

        deployments.each { deployment ->
            shell.info("Checking rollout status for ${deployment}")
            shell.run("kubectl rollout status -n ${shell.singleQuote(namespace)} ${shell.singleQuote(deployment)} --timeout=5m")
        }
    }
}
