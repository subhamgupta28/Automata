package dev.automata.automata.automation_engine.solar_api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public interface ISolarTimeProvider {
    Optional<LocalTime> getSunTime(String solarType, LocalDate date);
}
