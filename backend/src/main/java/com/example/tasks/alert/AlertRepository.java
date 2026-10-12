package com.example.tasks.alert;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

	List<Alert> findTop200ByOrderByTriggeredAtDescIdDesc();

	List<Alert> findTop200ByStatusOrderByTriggeredAtDescIdDesc(Alert.Status status);

	boolean existsByRuleIdAndPointDate(Long ruleId, LocalDate pointDate);

	long countByStatus(Alert.Status status);

	void deleteAllByRuleId(Long ruleId);

}
