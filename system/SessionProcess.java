package system;

/*
 * SessionProcess.java
 *
 * Small process container for Goddess Matrix.
 *
 * Responsibilities:
 * - hold a running Process
 * - hold stdin writer
 * - mark whether process belongs to SCRPT mode
 * - provide safe lifecycle helpers
 */

import java.io.PrintWriter;

public class SessionProcess {

    public Process process;
    public PrintWriter stdin;
    public boolean isScript;

    public SessionProcess() {
    }

    public SessionProcess(Process process, PrintWriter stdin, boolean isScript) {
        this.process = process;
        this.stdin = stdin;
        this.isScript = isScript;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public void sendLine(String line) {
        if (stdin != null) {
            stdin.println(line);
            stdin.flush();
        }
    }

    public void destroy() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public void destroyForcibly() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
