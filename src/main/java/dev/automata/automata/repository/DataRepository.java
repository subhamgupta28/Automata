package dev.automata.automata.repository;

import dev.automata.automata.model.Data;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRepository extends JpaRepository<Data, Integer> {
}