package com.kmercoders.nkap.financialinstitution;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialInstitutionRepository extends JpaRepository<FinancialInstitution, Long> {

    Optional<FinancialInstitution> findByKey(String key);

    boolean existsByKey(String key);
}
