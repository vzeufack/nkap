package com.kmercoders.nkap.category;

public class CategoryTransferResponse {

    private final CategoryDTO source;
    private final CategoryDTO target;

    public CategoryTransferResponse(CategoryDTO source, CategoryDTO target) {
        this.source = source;
        this.target = target;
    }

    public CategoryDTO getSource() { return source; }
    public CategoryDTO getTarget() { return target; }
}
