package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.CorteFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface CorteFarmaciaRepository extends JpaRepository<CorteFarmacia, LocalDate> {
}
