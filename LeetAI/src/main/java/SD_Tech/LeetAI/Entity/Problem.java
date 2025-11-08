	package SD_Tech.LeetAI.Entity;
	
	import jakarta.persistence.*;
	import lombok.*;
	import com.fasterxml.jackson.annotation.JsonIgnore;
	
	import java.util.ArrayList;
	import java.util.HashSet;
	import java.util.List;
	import java.util.Objects;
	import java.util.Set;
	
	@Entity
	@Table(name = "problems")
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public class Problem {
	
	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;
	
	    private String title;
	
	    @Column(length = 5000)
	    private String description;
	
	    private String difficulty; // EASY, MEDIUM, HARD
	
	    @Column(length = 2000)
	    private String example;
	
	    @Column(length = 2000)
	    private String constraints;
	
	    @Column(length = 5000)
	    private String explanation;
	
	    @Column(columnDefinition = "TEXT")
	    private String solution; // Solution code for validation
	
	    @Column(columnDefinition = "TEXT")
	    private String referenceSolution; // Reference solution for test case validation
	
	    private String referenceLanguage; // Language of the reference solution
	
	    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	    @JsonIgnore
	    private Set<Submission> submissions;
	
	    private boolean testCasesValidated = false;

	    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	    @JsonIgnore
	    private List<TestCase> testCases = new ArrayList<>();  // Changed to List
	
	    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	    @JsonIgnore
	    private Set<CodeTemplate> codeTemplates = new HashSet<>();
	
	    @ManyToMany(fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	    @JoinTable(
	            name = "problem_tags",
	            joinColumns = @JoinColumn(name = "problem_id"),
	            inverseJoinColumns = @JoinColumn(name = "tag_id")
	    )
	    private Set<Tag> tags;
	
	    // Helper method to add test case
	    public void addTestCase(TestCase testCase) {
	        if (testCases == null) {
	            testCases = new ArrayList<>();
	        }
	        testCases.add(testCase);
	        testCase.setProblem(this);
	    }
	
	    // Helper method to add code template
	    public void addCodeTemplate(CodeTemplate codeTemplate) {
	        if (codeTemplates == null) {
	            codeTemplates = new HashSet<>();
	        }
	        codeTemplates.add(codeTemplate);
	        codeTemplate.setProblem(this);
	    }
	
	    @Override
	    public boolean equals(Object o) {
	        if (this == o) return true;
	        if (o == null || getClass() != o.getClass()) return false;
	        Problem problem = (Problem) o;
	        return Objects.equals(id, problem.id);
	    }
	
	    @Override
	    public int hashCode() {
	        return Objects.hash(id);
	    }
	
	    @Override
	    public String toString() {
	        return "Problem{" +
	                "id=" + id +
	                ", title='" + title + '\'' +
	                ", difficulty='" + difficulty + '\'' +
	                '}';
	    }
	}