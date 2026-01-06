package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.Dashboard;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.VirtualDevice;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.VirtualDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VirtualDeviceService {

    private final VirtualDeviceRepository virtualDeviceRepository;
    private final NotificationService notificationService;
    private final DataRepository dataRepository;
    private final MongoTemplate mongoTemplate;

    public VirtualDevice getVirtualDevice(String vid) {
        return virtualDeviceRepository.findById(vid).orElse(null);
    }

    public List<VirtualDevice> getVirtualDeviceList() {
        return virtualDeviceRepository.findAll();
    }

    public VirtualDevice createVirtualDevice(VirtualDevice virtualDevice) {
        return virtualDeviceRepository.save(virtualDevice);
    }

    //    @Scheduled(fixedRate = 10000)
    public static String getLocationFromIP() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://api.weatherapi.com/v1/current.json?key=37271d83e0524746aa2152008260301&q=20.90,82.51&aqi=yes"))
                    .GET()
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.err.println(response.body());
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> weatherMap =
                    mapper.readValue(response.body(), Map.class);

            Map<String, Object> current =
                    (Map<String, Object>) weatherMap.get("current");

            Map<String, Object> condition =
                    (Map<String, Object>) current.get("condition");

            Map<String, Object> airQuality =
                    (Map<String, Object>) current.get("air_quality");


            String conditionText = (String) condition.get("text");


            Double windSpeedKph = ((Number) current.get("wind_kph")).doubleValue();
            Double windSpeedMph = ((Number) current.get("wind_mph")).doubleValue();


            String windDir = (String) current.get("wind_dir");
            Integer windDegree = ((Number) current.get("wind_degree")).intValue();

            Integer aqi = ((Number) airQuality.get("us-epa-index")).intValue();
            Map<String, Object> weatherSummary = Map.of(
                    "condition", conditionText,
                    "windSpeedKph", windSpeedKph,
                    "windDir", windDir,
                    "aqi", aqi
            );
            System.err.println(weatherSummary);
        }

        return response.body(); // JSON response
    }


    public String updateDevicePosition(String vid, String x, String y, String width, String height) {
        var device = virtualDeviceRepository.findById(vid).orElse(null);
        if (device == null)
            return "Device not found.";
        device.setX(Math.floor(Double.parseDouble(x)));
        device.setY(Math.floor(Double.parseDouble(y)));
        device.setWidth(Math.floor(Double.parseDouble(width)));
        device.setHeight(Math.floor(Double.parseDouble(height)));

        virtualDeviceRepository.save(device);
        notificationService.sendNotification("Devices positions updated", "success");
        return "success";
    }
//    @Scheduled(fixedRate = 10000)
//    public void test(){
//        System.err.println(getTodayStats("67dafae9fa67e36c0a25687e"));
//    }


    private static final double SAMPLE_INTERVAL_HOURS = 5.0 / 60.0;
    public Map<String, Double> getTodayStats(String deviceId) {

        ZoneId zone = ZoneId.of("Asia/Kolkata");

        long startOfDay = LocalDate.now(zone)
                .atStartOfDay(zone)
                .toEpochSecond();

        long now = Instant.now().getEpochSecond();

        Query query = new Query(
                Criteria.where("deviceId").is(deviceId)
                        .and("timestamp").gte(startOfDay).lte(now)
        );
        query.with(Sort.by(Sort.Direction.ASC, "timestamp"));

        List<Data> records = mongoTemplate.find(query, Data.class);

        Map<Integer, Double> dischargeHourlyWh = new HashMap<>();
        Map<Integer, Double> chargeHourlyWh = new HashMap<>();

        double dischargeTotalWh = 0.0;
        double chargeTotalWh = 0.0;

        var recordsLast = records.getLast();
        double percent = 0;
        if (recordsLast!=null){
            percent = Double.parseDouble((String) recordsLast.getData().get("percent"));
        }
        for (int i = 1; i < records.size(); i++) {

            Data prev = records.get(i - 1);
            Data curr = records.get(i);

            Object statusObj = prev.getData().get("status");
            Object powerObj = prev.getData().get("power");

            if (statusObj == null || powerObj == null) continue;

            String status = statusObj.toString();

            double powerW;
            try {
                powerW = Math.abs(Double.parseDouble(powerObj.toString()));
            } catch (NumberFormatException e) {
                continue;
            }

            long deltaSeconds = curr.getTimestamp() - prev.getTimestamp();

            // Guard against bad timestamps
            if (deltaSeconds <= 0 || deltaSeconds > 3600) {
                continue;
            }

            double energyWh = powerW * (deltaSeconds / 3600.0);

            int hour = Instant.ofEpochSecond(prev.getTimestamp())
                    .atZone(zone)
                    .getHour();

            if ("DISCHARGE".equals(status)) {
                dischargeTotalWh += energyWh;
                dischargeHourlyWh.merge(hour, energyWh, Double::sum);
            }

            if ("CHARGING".equals(status)) {
                chargeTotalWh += energyWh;
                chargeHourlyWh.merge(hour, energyWh, Double::sum);
            }
        }

        // Remove current incomplete hour
        int currentHour = LocalDateTime.now(zone).getHour();
        dischargeHourlyWh.remove(currentHour);
        chargeHourlyWh.remove(currentHour);

        double dischargePeakWh = 0;
        double dischargeLowestWh = 0;
        double chargePeakWh = 0;
        double chargeLowestWh = 0;

        if (!dischargeHourlyWh.isEmpty()) {
            dischargePeakWh = dischargeHourlyWh.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0);

            dischargeLowestWh = dischargeHourlyWh.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .min()
                    .orElse(0);
        }

        if (!chargeHourlyWh.isEmpty()) {
            chargePeakWh = chargeHourlyWh.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0);

            chargeLowestWh = chargeHourlyWh.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .min()
                    .orElse(0);
        }

        Map<String, Double> result = new HashMap<>();
        result.put("totalWh", round(dischargeTotalWh));
        result.put("peakWh", round(dischargePeakWh));
        result.put("lowestWh", round(dischargeLowestWh));

        result.put("chargeTotalWh", round(chargeTotalWh));
        result.put("chargePeakWh", round(chargePeakWh));
        result.put("chargeLowestWh", round(chargeLowestWh));
        result.put("percent", percent);

        return result;
    }


    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
