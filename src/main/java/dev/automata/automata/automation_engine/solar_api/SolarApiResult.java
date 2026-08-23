package dev.automata.automata.automation_engine.solar_api;

import lombok.Data;

import java.util.Date;

@Data
public class SolarApiResult {
    private Results results;
    private String status;
    private String tzid;

    @Data
    public static class Results {
        private Date sunrise;
        private Date sunset;
        private Date solar_noon;
        private int day_length;
        private Date civil_twilight_begin;
        private Date civil_twilight_end;
        private Date nautical_twilight_begin;
        private Date nautical_twilight_end;
        private Date astronomical_twilight_begin;
        private Date astronomical_twilight_end;
    }
}

