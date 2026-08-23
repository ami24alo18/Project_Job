package com.amit.jobagent.application;

import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationPackageIdempotencyAliasRepository
        extends JpaRepository<ApplicationPackageIdempotencyAlias, String> {}
