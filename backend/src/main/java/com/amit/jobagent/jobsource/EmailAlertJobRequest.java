package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.*;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record EmailAlertJobRequest(@Size(max=300)String externalId,@Size(max=200)String company,@Size(max=300)String title,@Size(max=300)String location,@Size(max=2)String countryCode,WorkplaceType workplaceType,EmploymentType employmentType,@Size(max=200)String department,@Size(max=200)String team,String description,@Size(max=2000)String applyUrl,@Size(max=2000)String sourceUrl,@PositiveOrZero BigDecimal salaryMinimum,@PositiveOrZero BigDecimal salaryMaximum,@Size(max=3)String salaryCurrency,SalaryInterval salaryInterval,Instant publishedAt,Instant expiresAt){}
