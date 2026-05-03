package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationAbTestLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AutomationAbTestLogRepository extends MongoRepository<AutomationAbTestLog, String> {
    long countByTestId(String id);

    long countByTestIdAndAgreedTrue(String id);

    Slice<AutomationAbTestLog> findByTestIdOrderByTimestampDesc(String id, PageRequest of);

    List<AutomationAbTestLog> findByTestIdAndAgreedFalseOrderByTimestampDesc(String id);

    long countByTestIdAndVariantATrue(String testId);

    long countByTestIdAndVariantBTrue(String testId);
}
