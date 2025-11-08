package SD_Tech.LeetAI.Entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "code_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 5000, nullable = false)
    private String code;

    @Column(nullable = false)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeTemplate)) return false;
        CodeTemplate that = (CodeTemplate) o;

        // ✅ Use id equality when both objects are persisted
        if (this.id != null && that.id != null) {
            return this.id.equals(that.id);
        }

        // ✅ For new (transient) entities, use language + code to distinguish
        return Objects.equals(this.language, that.language)
                && Objects.equals(this.code, that.code);
    }

    @Override
    public int hashCode() {
        // ✅ Use id hash if available, else fallback to language + code
        if (this.id != null) {
            return this.id.hashCode();
        }
        return Objects.hash(this.language, this.code);
    }

    @Override
    public String toString() {
        return "CodeTemplate{" +
                "id=" + id +
                ", language='" + language + '\'' +
                ", problemId=" + (problem != null ? problem.getId() : "null") +
                '}';
    }
}
