def call(List requiredList = ['aws', 'docker', 'terraform', 'kubectl', 'helm']) {
    def shell = new org.rdhcloudlab.Shell(this)
    shell.requireList('requiredList', requiredList)

    shell.info("Validating required tools: ${requiredList.join(', ')}")
    requiredList.each { toolName ->
        String tool = toolName?.toString()?.trim()
        if (!tool) {
            shell.fail("Required tool name is empty")
        }

        int status = shell.runStatus("command -v ${tool} >/dev/null 2>&1")
        if (status != 0) {
            shell.fail("Required tool '${tool}' not found in PATH. Install it and ensure PATH is set for the Jenkins agent.")
        }

        String versionCommand
        switch (tool) {
            case 'aws':
                versionCommand = 'aws --version'
                break
            case 'kubectl':
                versionCommand = 'kubectl version --client --short'
                break
            case 'terraform':
                versionCommand = 'terraform version'
                break
            case 'helm':
                versionCommand = 'helm version --short'
                break
            case 'docker':
                versionCommand = 'docker --version'
                break
            default:
                versionCommand = "${tool} --version"
        }

        String version = shell.runCapture("${versionCommand} 2>&1")
        shell.info("${tool} version: ${version}")
    }
}
