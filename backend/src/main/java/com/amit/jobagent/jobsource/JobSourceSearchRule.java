package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_source_search_rule")
class JobSourceSearchRule extends MutableEntity {
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 500)
    private String query;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> locations;
    @Column(name = "remote_allowed", nullable = false)
    private boolean remoteAllowed;
    @Column(name = "hybrid_allowed", nullable = false)
    private boolean hybridAllowed;
    @Column(name = "onsite_allowed", nullable = false)
    private boolean onsiteAllowed;
    @Enumerated(EnumType.STRING)
    @Column(name = "date_posted_window", nullable = false, length = 30)
    private DatePostedWindow datePostedWindow;
    @Column(name = "maximum_results", nullable = false)
    private int maximumResults;
    @Column(nullable = false)
    private boolean enabled;

    protected JobSourceSearchRule() {}

    JobSourceSearchRule(UUID sourceId, JobSourceSearchRuleRequest request) {
        this.sourceId = sourceId;
        apply(request, true);
    }

    void apply(JobSourceSearchRuleRequest request, boolean initial) {
        name = request.name();
        query = request.query();
        locations = List.copyOf(request.locations());
        remoteAllowed = request.remoteAllowed();
        hybridAllowed = request.hybridAllowed();
        onsiteAllowed = request.onsiteAllowed();
        datePostedWindow = request.datePostedWindow();
        maximumResults = request.maximumResults();
        enabled = initial
                ? request.enabled() == null || request.enabled()
                : request.enabled() == null ? enabled : request.enabled();
        if (!initial) touch();
    }

    void setEnabled(boolean value) { enabled = value; touch(); }
    UUID sourceId() { return sourceId; }
    String name() { return name; }
    String query() { return query; }
    List<String> locations() { return locations; }
    boolean remoteAllowed() { return remoteAllowed; }
    boolean hybridAllowed() { return hybridAllowed; }
    boolean onsiteAllowed() { return onsiteAllowed; }
    DatePostedWindow datePostedWindow() { return datePostedWindow; }
    int maximumResults() { return maximumResults; }
    boolean enabled() { return enabled; }
}
