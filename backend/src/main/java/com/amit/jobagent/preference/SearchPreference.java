package com.amit.jobagent.preference;
import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
@Entity @Table(name="search_preference")
class SearchPreference extends MutableEntity {
    @Column(name="profile_id",nullable=false,unique=true,updatable=false) private UUID profileId;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_target_title",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> targetTitles=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_preferred_location",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> preferredLocations=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_excluded_location",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> excludedLocations=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_required_skill",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> requiredSkills=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_preferred_skill",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> preferredSkills=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_excluded_company",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> excludedCompanies=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="search_preference_excluded_keyword",joinColumns=@JoinColumn(name="preference_id")) @Column(name="preference_value") private Set<String> excludedKeywords=new LinkedHashSet<>();
    @Column(name="minimum_experience_years",nullable=false) private int minimumExperienceYears;
    @Column(name="maximum_experience_years",nullable=false) private int maximumExperienceYears;
    @Column(name="minimum_match_score",nullable=false) private int minimumMatchScore;
    @Column(name="maximum_daily_shortlist",nullable=false) private int maximumDailyShortlist;
    @Column(name="maximum_daily_applications",nullable=false) private int maximumDailyApplications;
    @Column(name="remote_allowed",nullable=false) private boolean remoteAllowed;
    @Column(name="hybrid_allowed",nullable=false) private boolean hybridAllowed;
    @Column(name="onsite_allowed",nullable=false) private boolean onsiteAllowed;
    protected SearchPreference(){}
    SearchPreference(UUID profileId,SearchPreferenceRequest r){this.profileId=profileId;apply(r);}
    void apply(SearchPreferenceRequest r){targetTitles=copy(r.targetTitles());preferredLocations=copy(r.preferredLocations());excludedLocations=copy(r.excludedLocations());requiredSkills=copy(r.requiredSkills());preferredSkills=copy(r.preferredSkills());excludedCompanies=copy(r.excludedCompanies());excludedKeywords=copy(r.excludedKeywords());minimumExperienceYears=r.minimumExperienceYears();maximumExperienceYears=r.maximumExperienceYears();minimumMatchScore=r.minimumMatchScore();maximumDailyShortlist=r.maximumDailyShortlist();maximumDailyApplications=r.maximumDailyApplications();remoteAllowed=r.remoteAllowed();hybridAllowed=r.hybridAllowed();onsiteAllowed=r.onsiteAllowed();}
    private static Set<String> copy(Set<String> values){return new LinkedHashSet<>(values);}
    UUID profileId(){return profileId;} Set<String> targetTitles(){return Set.copyOf(targetTitles);} Set<String> preferredLocations(){return Set.copyOf(preferredLocations);} Set<String> excludedLocations(){return Set.copyOf(excludedLocations);} Set<String> requiredSkills(){return Set.copyOf(requiredSkills);} Set<String> preferredSkills(){return Set.copyOf(preferredSkills);} Set<String> excludedCompanies(){return Set.copyOf(excludedCompanies);} Set<String> excludedKeywords(){return Set.copyOf(excludedKeywords);} int minimumExperienceYears(){return minimumExperienceYears;} int maximumExperienceYears(){return maximumExperienceYears;} int minimumMatchScore(){return minimumMatchScore;} int maximumDailyShortlist(){return maximumDailyShortlist;} int maximumDailyApplications(){return maximumDailyApplications;} boolean remoteAllowed(){return remoteAllowed;} boolean hybridAllowed(){return hybridAllowed;} boolean onsiteAllowed(){return onsiteAllowed;}
}
