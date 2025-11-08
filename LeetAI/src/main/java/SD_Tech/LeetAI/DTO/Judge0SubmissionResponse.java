package SD_Tech.LeetAI.DTO;

public class Judge0SubmissionResponse {
    private String token;
    private Judge0Status status;
    private String stdout;
    private String stderr;
    private String compile_output;
    private long time;
    private long memory;
    private int exit_code;
    private int exit_signal;

    // Default constructor
    public Judge0SubmissionResponse() {}

    // Getters and setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Judge0Status getStatus() {
        return status;
    }

    public void setStatus(Judge0Status status) {
        this.status = status;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getCompile_output() {
        return compile_output;
    }

    public void setCompile_output(String compile_output) {
        this.compile_output = compile_output;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getMemory() {
        return memory;
    }

    public void setMemory(long memory) {
        this.memory = memory;
    }

    public int getExit_code() {
        return exit_code;
    }

    public void setExit_code(int exit_code) {
        this.exit_code = exit_code;
    }

    public int getExit_signal() {
        return exit_signal;
    }

    public void setExit_signal(int exit_signal) {
        this.exit_signal = exit_signal;
    }
}