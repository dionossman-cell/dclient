package com.dclient.module;

public enum Category {
    COMBAT("Combat"),
    MISC("Misc"),
    DONUT("Donut"),
    RENDER("Render"),
    VISUALS("Visuals"),
    CLIENT("Client");

    public final String name;

    Category(String name) {
        this.name = name;
    }
}
