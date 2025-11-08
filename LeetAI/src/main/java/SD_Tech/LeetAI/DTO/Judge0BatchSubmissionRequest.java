package SD_Tech.LeetAI.DTO;

public class Judge0BatchSubmissionRequest {
    private String language_id;
    private String source_code;
    private String stdin;
    private String expected_output;
    private int cpu_time_limit = 2; // Default CPU time limit
    private int memory_limit = 128; // Default memory limit in MB

    // Default constructor
    public Judge0BatchSubmissionRequest() {}

    // Constructor with required fields
    public Judge0BatchSubmissionRequest(String language_id, String source_code, String stdin) {
        this.language_id = language_id;
        this.source_code = source_code;
        this.stdin = stdin;
    }

    // Getters and setters
    public String getLanguage_id() {
        return language_id;
    }

    public void setLanguage_id(String language_id) {
        this.language_id = language_id;
    }

    public String getSource_code() {
        return source_code;
    }

    public void setSource_code(String source_code) {
        this.source_code = source_code;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }

    public String getExpected_output() {
        return expected_output;
    }

    public void setExpected_output(String expected_output) {
        this.expected_output = expected_output;
    }

    public int getCpu_time_limit() {
        return cpu_time_limit;
    }

    public void setCpu_time_limit(int cpu_time_limit) {
        this.cpu_time_limit = cpu_time_limit;
    }

    public int getMemory_limit() {
        return memory_limit;
    }

    public void setMemory_limit(int memory_limit) {
        this.memory_limit = memory_limit;
    }
}