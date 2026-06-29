package com.portfolio.attribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributionResultRepository extends JpaRepository<AttributionResultEntity, String> {
}
