package dev.automata.automata.automation_engine.helpers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

@Slf4j
@Component
public class ScheduleHelper {

    public static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(s.trim(),
                        new DateTimeFormatterBuilder().parseCaseInsensitive()
                                .appendPattern("hh:mm:ss a").toFormatter(Locale.ENGLISH));
            } catch (Exception e2) {
                log.warn("⚠️ Unable to parse time: '{}'", s);
                return null;
            }
        }
    }
}
