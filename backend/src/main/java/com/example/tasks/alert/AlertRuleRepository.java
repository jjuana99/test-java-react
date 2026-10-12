package com.example.tasks.alert;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

	List<AlertRule> findAllByOrderByCreatedAtAsc();

	List<AlertRule> findAllByEnabledTrueAndSeriesCodeIn(Collection<String> seriesCodes);

}
