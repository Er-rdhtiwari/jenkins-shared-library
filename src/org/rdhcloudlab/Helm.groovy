package org.rdhcloudlab

class Helm implements Serializable {
    Shell shell
    Kubernetes kube

    Helm(steps) {
        this.shell = new Shell(steps)
        this.kube = new Kubernetes(steps)
    }

    void deploy(String releaseName, String chartPath, String namespace, List valuesFilesList, Map setParamsMap) {
        shell.requireNonEmpty("releaseName", releaseName)
        shell.requireNonEmpty("chartPath", chartPath)
        shell.requireNonEmpty("namespace", namespace)
        shell.requireMap("setParamsMap", setParamsMap)
        shell.requireListOptional("valuesFilesList", valuesFilesList)
        shell.requirePathExists("chartPath", chartPath)

        List valuesFiles = valuesFilesList ?: []
        valuesFiles.each { filePath ->
            String resolvedPath = filePath?.toString()?.trim()
            shell.requireNonEmpty("valuesFile", resolvedPath)
            shell.requireFileExists("valuesFile", resolvedPath)
        }
        kube.ensureNamespace(namespace)

        String valuesArgs = valuesFiles.collect { "-f ${shell.singleQuote(it.toString())}" }.join(" ")
        String setArgs = buildSetArgs(setParamsMap ?: [:])

        shell.info("Deploying Helm release ${releaseName} into ${namespace}")
        shell.run("helm upgrade --install ${shell.singleQuote(releaseName)} ${shell.singleQuote(chartPath)} --namespace ${shell.singleQuote(namespace)} --atomic --wait --timeout 10m ${valuesArgs} ${setArgs}".trim())

        shell.info("Fetching Helm status for ${releaseName}")
        shell.run("helm status ${shell.singleQuote(releaseName)} --namespace ${shell.singleQuote(namespace)}")

        List<String> deployments = kube.deploymentsForRelease(releaseName, namespace)
        kube.rolloutStatus(deployments, namespace)
    }

    void rollback(String releaseName, int revision, String namespace) {
        shell.requireNonEmpty("releaseName", releaseName)
        shell.requireNonEmpty("namespace", namespace)
        if (revision <= 0) {
            shell.fail("Revision must be a positive integer")
        }

        shell.info("Rolling back Helm release ${releaseName} to revision ${revision}")
        shell.run("helm rollback ${shell.singleQuote(releaseName)} ${revision} --namespace ${shell.singleQuote(namespace)} --wait --timeout 10m")
        shell.run("helm status ${shell.singleQuote(releaseName)} --namespace ${shell.singleQuote(namespace)}")
    }

    String buildSetArgs(Map setParams) {
        if (setParams == null || setParams.isEmpty()) {
            return ""
        }

        List<String> parts = []
        setParams.each { key, value ->
            if (value instanceof Boolean || value instanceof Number) {
                parts << "--set ${key}=${value}"
            } else {
                parts << "--set-string ${key}=${shell.singleQuote(value.toString())}"
            }
        }
        return parts.join(" ")
    }
}
