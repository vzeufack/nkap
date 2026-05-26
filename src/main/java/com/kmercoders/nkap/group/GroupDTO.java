package com.kmercoders.nkap.group;

public class GroupDTO {

    private Long id;
    private String name;
    private boolean isDefault;

    public GroupDTO() {}

    public GroupDTO(String name) {
        this.name = name;
    }

    public GroupDTO(Long id, String name, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isDefault() { return isDefault; }
}
