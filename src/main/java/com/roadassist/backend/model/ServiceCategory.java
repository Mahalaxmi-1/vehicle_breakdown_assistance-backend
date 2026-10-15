package com.roadassist.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_categories")
public class ServiceCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(nullable = false, length = 150)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected ServiceCategory() { }

    public ServiceCategory(String name, String description) {
        this.name = name;
        this.description = description;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
