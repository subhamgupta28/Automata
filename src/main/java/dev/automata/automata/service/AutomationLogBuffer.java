package dev.automata.automata.service;


import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationLogBuffer {

    private final AutomationLogRepository automationLogRepository;

    // Lock-free queue — multiple automation threads write, one scheduler thread drains
    private final ConcurrentLinkedQueue<AutomationLog> buffer = new ConcurrentLinkedQueue<>();

    public void add(AutomationLog log) {
        buffer.offer(log);
    }

    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        if (buffer.isEmpty()) return;

        List<AutomationLog> batch = new ArrayList<>();
        AutomationLog entry;
        while ((entry = buffer.poll()) != null) {
            batch.add(entry);
        }

        if (batch.isEmpty()) return;

        try {
            automationLogRepository.saveAll(batch);
            log.debug("Flushed {} automation log entries", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush automation log batch: {}", e.getMessage());
            // Re-queue on failure so logs aren't lost
            batch.forEach(buffer::offer);
        }
    }
}