package com.amit.jobagent.profile.answer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface ReusableAnswerRepository extends JpaRepository<ReusableAnswer,UUID>{Optional<ReusableAnswer>findByProfileIdAndNormalizedQuestion(UUID profileId,String normalizedQuestion);List<ReusableAnswer>findByProfileIdOrderByCreatedAtDescIdAsc(UUID profileId);List<ReusableAnswer>findByProfileIdAndStatusOrderByCreatedAtAscIdAsc(UUID profileId,ReusableAnswerStatus status);}
