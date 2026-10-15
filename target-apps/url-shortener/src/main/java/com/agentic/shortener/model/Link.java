package com.agentic.shortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A short link: a unique code (generated or a custom alias) pointing to a long URL. */
@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Soft delete: the row stays so the code can never be handed out again. */
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    protected Link() {
        // required by JPA
    }

    public Link(String code, String longUrl, Instant createdAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLongUrl() { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deleted; }

    public void markDeleted() { this.deleted = true; }
}
