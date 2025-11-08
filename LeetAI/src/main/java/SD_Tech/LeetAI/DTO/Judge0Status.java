package SD_Tech.LeetAI.DTO;

public class Judge0Status {
    private int id;
    private String description;

    // Default constructor
    public Judge0Status() {}

    // Constructor with fields
    public Judge0Status(int id, String description) {
        this.id = id;
        this.description = description;
    }

    // Getters and setters
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