package com.example.tasks.ingest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

	Optional<IngestionRun> findFirstBySourceOrderByStartedAtDesc(String source);

	List<IngestionRun> findTop50ByOrderByStartedAtDesc();

	List<IngestionRun> findAllByStatus(IngestionRun.Status status);

}
