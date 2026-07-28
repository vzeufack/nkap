package com.kmercoders.nkap.financialinstitution;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial-institutions")
public class FinancialInstitutionController {

    private final FinancialInstitutionService financialInstitutionService;

    public FinancialInstitutionController(FinancialInstitutionService financialInstitutionService) {
        this.financialInstitutionService = financialInstitutionService;
    }

    @GetMapping
    public ResponseEntity<List<FinancialInstitutionDTO>> getFinancialInstitutions() {
        return ResponseEntity.ok(financialInstitutionService.getAllFinancialInstitutions());
    }
}
