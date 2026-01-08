def call(String envDir, String action, Map extraVarsMap = [:]) {
    def shell = new org.rdhcloudlab.Shell(this)
    shell.requireNonEmpty('envDir', envDir)
    shell.requireNonEmpty('action', action)
    shell.requireMap('extraVarsMap', extraVarsMap)
    shell.requireDirExists('envDir', envDir)

    String normalizedAction = action.toString().toLowerCase().trim()
    if (!['plan', 'apply', 'destroy'].contains(normalizedAction)) {
        shell.fail("Unsupported terraform action '${action}'. Use plan, apply, or destroy.")
    }

    Map<String, Object> vars = [:]
    boolean requireApproval = false
    String approvalMessage = null
    String planFile = null

    (extraVarsMap ?: [:]).each { key, value ->
        String k = key.toString()
        if (k.startsWith('_')) {
            if (k == '_requireApproval') {
                requireApproval = value?.toString()?.toBoolean()
            }
            if (k == '_approvalMessage') {
                approvalMessage = value?.toString()
            }
            if (k == '_planFile') {
                planFile = value?.toString()
            }
        } else {
            if (value == null) {
                shell.fail("Terraform var '${k}' is null. Provide a value.")
            }
            vars[k] = value
        }
    }

    if (!planFile) {
        planFile = normalizedAction == 'destroy' ? 'tfplan-destroy' : 'tfplan'
    }

    List<String> varArgs = []
    vars.each { key, value ->
        varArgs << "-var ${shell.singleQuote("${key}=${value}")}"
    }
    String varArgString = varArgs.join(' ')

    dir(envDir) {
        shell.info("Running terraform init in ${envDir}")
        shell.run('terraform init -input=false')

        shell.info('Running terraform fmt check')
        shell.run('terraform fmt -check -recursive')

        shell.info('Running terraform validate')
        shell.run('terraform validate')

        if (normalizedAction == 'plan') {
            shell.info('Running terraform plan')
            shell.run("terraform plan -input=false ${varArgString} -out=${planFile}")
            shell.run("terraform show -no-color ${planFile} > ${planFile}.txt")
            shell.info("Terraform plan saved to ${envDir}/${planFile} and ${envDir}/${planFile}.txt")
            return
        }

        if (normalizedAction == 'apply') {
            shell.info('Running terraform plan before apply')
            shell.run("terraform plan -input=false ${varArgString} -out=${planFile}")
            shell.run("terraform show -no-color ${planFile} > ${planFile}.txt")

            if (requireApproval) {
                String message = approvalMessage ?: "Approve terraform apply for ${envDir}"
                gateApprove(message)
            }

            shell.info('Applying terraform plan')
            shell.run("terraform apply -input=false -auto-approve ${planFile}")
            return
        }

        if (normalizedAction == 'destroy') {
            shell.info('Running terraform plan -destroy')
            shell.run("terraform plan -destroy -input=false ${varArgString} -out=${planFile}")
            shell.run("terraform show -no-color ${planFile} > ${planFile}.txt")

            if (requireApproval) {
                String message = approvalMessage ?: "Approve terraform destroy for ${envDir}"
                gateApprove(message)
            }

            shell.info('Applying terraform destroy plan')
            shell.run("terraform apply -input=false -auto-approve ${planFile}")
        }
    }
}
