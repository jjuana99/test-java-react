package com.example.tasks.series;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesRepository extends JpaRepository<Series, Long> {

	Optional<Series> findByCode(String code);

	List<Series> findAllByOrderBySourceAscCodeAsc();

	List<Series> findAllBySourceOrderByCodeAsc(String source);

}
