package com.amit.jobagent.profile;

import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity @Table(name="candidate_profile")
class CandidateProfile extends MutableEntity {
    @Column(name="singleton_key",nullable=false,updatable=false) private boolean singletonKey=true;
    @Column(name="full_name",nullable=false,length=150) private String fullName;
    @Column(nullable=false,length=254) private String email;
    @Column(length=40) private String phone;
    @Column(name="professional_title",nullable=false,length=150) private String professionalTitle;
    @Column(name="professional_summary",length=4000) private String professionalSummary;
    @Column(name="current_company",length=200) private String currentCompany;
    @Column(name="current_location",length=200) private String currentLocation;
    @Column(name="total_experience_months",nullable=false) private int totalExperienceMonths;
    @Column(name="notice_period_days",nullable=false) private int noticePeriodDays;
    @Column(name="serving_notice_period",nullable=false) private boolean servingNoticePeriod;
    @Column(name="linkedin_url",length=500) private String linkedinUrl;
    @Column(name="github_url",length=500) private String githubUrl;
    @Column(name="leetcode_url",length=500) private String leetcodeUrl;
    @Column(name="portfolio_url",length=500) private String portfolioUrl;
    protected CandidateProfile() {}
    CandidateProfile(CandidateProfileRequest r) { apply(r); }
    void apply(CandidateProfileRequest r) {
        fullName=r.fullName().trim(); email=r.email().trim(); phone=clean(r.phone()); professionalTitle=r.professionalTitle().trim();
        professionalSummary=clean(r.professionalSummary()); currentCompany=clean(r.currentCompany()); currentLocation=clean(r.currentLocation());
        totalExperienceMonths=r.totalExperienceMonths(); noticePeriodDays=r.noticePeriodDays(); servingNoticePeriod=r.servingNoticePeriod();
        linkedinUrl=clean(r.linkedinUrl()); githubUrl=clean(r.githubUrl()); leetcodeUrl=clean(r.leetcodeUrl()); portfolioUrl=clean(r.portfolioUrl());
        if (getId() != null) touch();
    }
    private static String clean(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    String fullName(){return fullName;} String email(){return email;} String phone(){return phone;}
    String professionalTitle(){return professionalTitle;} String professionalSummary(){return professionalSummary;}
    String currentCompany(){return currentCompany;} String currentLocation(){return currentLocation;}
    int totalExperienceMonths(){return totalExperienceMonths;} int noticePeriodDays(){return noticePeriodDays;}
    boolean servingNoticePeriod(){return servingNoticePeriod;} String linkedinUrl(){return linkedinUrl;}
    String githubUrl(){return githubUrl;} String leetcodeUrl(){return leetcodeUrl;} String portfolioUrl(){return portfolioUrl;}
}
