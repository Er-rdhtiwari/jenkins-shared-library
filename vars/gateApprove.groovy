def call(String message) {
    def shell = new org.rdhcloudlab.Shell(this)
    shell.requireNonEmpty('message', message)

    shell.info("Awaiting approval: ${message}")
    input(message: message, ok: 'Proceed')
}
