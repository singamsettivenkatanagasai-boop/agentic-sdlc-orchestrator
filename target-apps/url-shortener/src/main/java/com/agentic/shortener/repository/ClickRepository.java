package com.agentic.shortener.repository;

import com.agentic.shortener.model.Click;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickRepository extends JpaRepository<Click, Long> {

    List<Click> findByLinkId(Long linkId);
}
