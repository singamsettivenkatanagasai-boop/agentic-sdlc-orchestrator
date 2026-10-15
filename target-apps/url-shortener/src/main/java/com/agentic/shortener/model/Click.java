package com.agentic.shortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One redirect of a link, kept for the statistics endpoint. */
@Entity
@Table(name = "clicks")
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "referrer", length = 1024)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected Click() {
        // required by JPA
    }

    public Click(Long linkId, Instant clickedAt, String referrer, String userAgent) {
        this.linkId = linkId;
        this.clickedAt = clickedAt;
        this.referrer = truncate(referrer, 1024);
        this.userAgent = truncate(userAgent, 512);
    }

    public Long getId() { return id; }
    public Long getLinkId() { return linkId; }
    public Instant getClickedAt() { return clickedAt; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
