package SD_Tech.LeetAI.DTO;

public class Judge0Response {
    private String token;
    private Status status;
    private String stdout;
    private String stderr;
    private String compile_output;
    private long time;
    private long memory;
    
    // Getters and Setters
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
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
    
    // Status nested class
    public static class Status {
        private int id;
        private String description;
        
        // Getters and Setters
        public int getId() {
            return id;
        }
        
        public void setId(int id) {
            this.id = id;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
}