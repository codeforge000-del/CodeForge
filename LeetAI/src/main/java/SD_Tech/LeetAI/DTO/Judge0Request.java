package SD_Tech.LeetAI.DTO;

public class Judge0Request {
    private String source_code;
    private Integer language_id;
    private String stdin;
    private Double cpu_time_limit;
    private Integer memory_limit;

    // Getters and setters
    public String getSource_code() { return source_code; }
    public void setSource_code(String source_code) { this.source_code = source_code; }

    public Integer getLanguage_id() { return language_id; }
    public void setLanguage_id(Integer language_id) { this.language_id = language_id; }

    public String getStdin() { return stdin; }
    public void setStdin(String stdin) { this.stdin = stdin; }

    public Double getCpu_time_limit() { return cpu_time_limit; }
    public void setCpu_time_limit(Double cpu_time_limit) { this.cpu_time_limit = cpu_time_limit; }

    public Integer getMemory_limit() { return memory_limit; }
    public void setMemory_limit(Integer memory_limit) { this.memory_limit = memory_limit; }
}
