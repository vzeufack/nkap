package com.kmercoders.nkap.bulkupload.csv;

import java.util.List;

public class CsvValidationException extends RuntimeException {

    private final List<String> errors;

    public CsvValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
