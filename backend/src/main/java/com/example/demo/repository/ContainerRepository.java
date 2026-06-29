package com.example.demo.repository;

import com.example.demo.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContainerRepository extends JpaRepository<Container, Long> {
    List<Container> findByType(String type);
}