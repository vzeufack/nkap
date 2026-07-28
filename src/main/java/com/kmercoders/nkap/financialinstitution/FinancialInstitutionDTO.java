package com.kmercoders.nkap.financialinstitution;

public class FinancialInstitutionDTO {

    private final Long id;
    private final String key;
    private final String displayName;

    public FinancialInstitutionDTO(Long id, String key, String displayName) {
        this.id          = id;
        this.key         = key;
        this.displayName = displayName;
    }

    public static FinancialInstitutionDTO from(FinancialInstitution financialInstitution) {
        return new FinancialInstitutionDTO(
            financialInstitution.getId(),
            financialInstitution.getKey(),
            financialInstitution.getDisplayName()
        );
    }

    public Long getId()            { return id; }
    public String getKey()         { return key; }
    public String getDisplayName() { return displayName; }
}
