package com.example.demo.repository;

import com.example.demo.entity.AiUserConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiUserConfigRepository extends JpaRepository<AiUserConfig, Long> {
    Optional<AiUserConfig> findByUsername(String username);
}
