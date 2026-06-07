package dev.automata.automata.repository;

import dev.automata.automata.model.LoginHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends MongoRepository<LoginHistory, String> {
    
    List<LoginHistory> findByEmail(String email);
    
    List<LoginHistory> findByEmailOrderByLoginTimeDesc(String email);
    
    List<LoginHistory> findByLoginTimeAfterOrderByLoginTimeDesc(Instant after);
    
    List<LoginHistory> findByLoginTimeBetweenOrderByLoginTimeDesc(Instant start, Instant end);
    
    List<LoginHistory> findByEmailAndLoginTimeAfterOrderByLoginTimeDesc(String email, Instant after);
    
    List<LoginHistory> findByUserIdOrderByLoginTimeDesc(String userId);
    
    @Query("{'success': true}")
    List<LoginHistory> findAllSuccessfulLogins();
    
    @Query("{'success': false}")
    List<LoginHistory> findAllFailedLogins();
    
    @Query("{$group: {_id: '$email', count: {$sum: 1}, lastLogin: {$max: '$loginTime'}}}")
    List<?> getLoginCountByUser();
}
