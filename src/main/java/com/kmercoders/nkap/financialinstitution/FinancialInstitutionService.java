package com.kmercoders.nkap.financialinstitution;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class FinancialInstitutionService {

    private final FinancialInstitutionRepository financialInstitutionRepository;

    public FinancialInstitutionService(FinancialInstitutionRepository financialInstitutionRepository) {
        this.financialInstitutionRepository = financialInstitutionRepository;
    }

    public List<FinancialInstitutionDTO> getAllFinancialInstitutions() {
        return financialInstitutionRepository.findAll().stream()
            .map(FinancialInstitutionDTO::from)
            .sorted(Comparator.comparing(FinancialInstitutionDTO::getDisplayName))
            .toList();
    }
}
