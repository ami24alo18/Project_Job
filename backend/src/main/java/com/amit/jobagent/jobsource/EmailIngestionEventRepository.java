package com.amit.jobagent.jobsource;
import java.util.Optional;import java.util.UUID;import org.springframework.data.jpa.repository.JpaRepository;
interface EmailIngestionEventRepository extends JpaRepository<EmailIngestionEvent,UUID>{Optional<EmailIngestionEvent>findByMessageId(String messageId);}
