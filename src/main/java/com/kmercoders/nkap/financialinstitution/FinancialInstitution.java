package com.kmercoders.nkap.financialinstitution;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_institution")
public class FinancialInstitution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_key", nullable = false, unique = true)
    private String key;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    public FinancialInstitution() {}

    public FinancialInstitution(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public Long getId() { return id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
