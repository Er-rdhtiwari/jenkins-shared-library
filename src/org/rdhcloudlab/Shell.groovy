package org.rdhcloudlab

class Shell implements Serializable {
    def steps

    Shell(steps) {
        this.steps = steps
    }

    void info(String message) {
        steps.echo("[rdh-lib] ${message}")
    }

    void warn(String message) {
        steps.echo("[rdh-lib][WARN] ${message}")
    }

    void fail(String message) {
        steps.error("[rdh-lib] ${message}")
    }

    void requireNonEmpty(String name, Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            fail("Missing required parameter: ${name}")
        }
    }

    void requireList(String name, Object value) {
        if (!(value instanceof List) || value.isEmpty()) {
            fail("Missing required list: ${name}")
        }
    }

    void requireMap(String name, Object value) {
        if (value != null && !(value instanceof Map)) {
            fail("Expected ${name} to be a map")
        }
    }

    void requireListOptional(String name, Object value) {
        if (value != null && !(value instanceof List)) {
            fail("Expected ${name} to be a list")
        }
    }

    void requirePathExists(String name, String path) {
        requireNonEmpty(name, path)
        int status = runStatus("test -e ${singleQuote(path)}")
        if (status != 0) {
            fail("${name} not found: ${path}")
        }
    }

    void requireFileExists(String name, String path) {
        requireNonEmpty(name, path)
        int status = runStatus("test -f ${singleQuote(path)}")
        if (status != 0) {
            fail("${name} not found or not a file: ${path}")
        }
    }

    void requireDirExists(String name, String path) {
        requireNonEmpty(name, path)
        int status = runStatus("test -d ${singleQuote(path)}")
        if (status != 0) {
            fail("${name} not found or not a directory: ${path}")
        }
    }

    void run(String command) {
        steps.sh(script: command)
    }

    int runStatus(String command) {
        steps.sh(returnStatus: true, script: command)
    }

    String runCapture(String command) {
        steps.sh(returnStdout: true, script: command).trim()
    }

    String shellEscape(String value) {
        if (value == null) {
            return ""
        }
        return value.replace("'", "'\"'\"'")
    }

    String singleQuote(String value) {
        return "'${shellEscape(value)}'"
    }
}
