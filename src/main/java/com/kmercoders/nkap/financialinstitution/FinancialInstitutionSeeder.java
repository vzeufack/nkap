package com.kmercoders.nkap.financialinstitution;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class FinancialInstitutionSeeder implements CommandLineRunner {

    private final FinancialInstitutionRepository financialInstitutionRepository;

    public FinancialInstitutionSeeder(FinancialInstitutionRepository financialInstitutionRepository) {
        this.financialInstitutionRepository = financialInstitutionRepository;
    }

    @Override
    public void run(String... args) {
        List<FinancialInstitution> defaults = List.of(
            new FinancialInstitution("BANK_OF_AMERICA", "Bank of America"),
            new FinancialInstitution("CAPITAL_ONE", "Capital One")
        );

        for (FinancialInstitution financialInstitution : defaults) {
            if (!financialInstitutionRepository.existsByKey(financialInstitution.getKey())) {
                financialInstitutionRepository.save(financialInstitution);
            }
        }
    }
}
