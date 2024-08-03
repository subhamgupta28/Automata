package dev.automata.automata.repository;

import dev.automata.automata.model.Attribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    List<Attribute> findAllByDeviceId(Long deviceId);
}