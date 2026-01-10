package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.Dashboard;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.EnergyStat;
import dev.automata.automata.model.VirtualDevice;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.EnergyStatRepository;
import dev.automata.automata.repository.VirtualDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VirtualDeviceService {

    private final VirtualDeviceRepository virtualDeviceRepository;
    private final NotificationService notificationService;
    private final DataRepository dataRepository;
    private final MongoTemplate mongoTemplate;
    private final EnergyStatRepository energyStatRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VirtualDevice getVirtualDevice(String vid) {
        return virtualDeviceRepository.findById(vid).orElse(null);
    }

    public List<VirtualDevice> getVirtualDeviceList() {
//        var list = virtualDeviceRepository.findAll();
//        var finalList = new ArrayList<VirtualDevice>();
//        ObjectMapper mapper = new ObjectMapper();
//        for (var device : list){
//            if (device.getTag().equals("Energy")){
//                var energy = getLastEnergyStat(device);
//                device.setRecentData(mapper.convertValue(energy, Map.class));
//            }
//            if (device.getTag().equals("Weather")){
//                device.setRecentData(getRecentDeviceData(device.getDeviceIds()));
//            }
//            finalList.add(device);
//        }
        return virtualDeviceRepository.findAll();
    }
    public Map<String, Object> getLastData(String deviceId) {
        var data = dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(deviceId).orElse(new Data());
        return data.getData();
    }

    public Map<String, Object> getRecentDeviceData(List<String> deviceIds){
        var map = new HashMap<String, Object>();
        for (var id : deviceIds){
            map.put(id, getLastData(id));
        }

        return map;
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

    private EnergyStat getLastEnergyStat(VirtualDevice virtualDevice){
        double percent = 0;
        var energyStat = new EnergyStat();
        for (var item : virtualDevice.getDeviceIds()) {
            var res = getTodayStats(item);
            energyStat.setTotalWh(energyStat.getTotalWh() + res.getTotalWh());
            energyStat.setPeakWh(energyStat.getPeakWh() + res.getPeakWh());
            energyStat.setLowestWh(energyStat.getLowestWh() + res.getLowestWh());
            energyStat.setTotalWhTrend(energyStat.getTotalWhTrend() + res.getTotalWhTrend());
            energyStat.setPeakWhTrend(energyStat.getPeakWhTrend() + res.getPeakWhTrend());
            energyStat.setLowestWhTrend(energyStat.getLowestWhTrend() + res.getLowestWhTrend());
            energyStat.setPercentTrend(energyStat.getPercentTrend() + res.getPercentTrend());
            percent += res.getPercent();
            energyStat.setChargeLowestWh(energyStat.getChargeLowestWh() + res.getChargeLowestWh());
            energyStat.setChargePeakWh(energyStat.getChargePeakWh() + res.getChargePeakWh());
            energyStat.setChargeTotalWh(energyStat.getChargeTotalWh() + res.getChargeTotalWh());
        }
        percent = percent / virtualDevice.getDeviceIds().size();
        energyStat.setPercentTrend(energyStat.getPercentTrend() < 0 ? Math.abs(energyStat.getPercentTrend()) : energyStat.getPercentTrend());
        energyStat.setPercent(percent);
        return energyStat;
    }

    @Scheduled(fixedRate = 2 * 60 * 1000) // every 2 min
    public void updateEnergyStat() {
        var virtualDevice = virtualDeviceRepository.findAllByTag("Energy");
        var device = virtualDevice.getFirst();
        var energyStat = getLastEnergyStat(device);
        messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", device.getId(), "data", energyStat));
    }

//    public Map<String, Object> getLastEnergyStat(String deviceId) {
//        var data = dataRepository.getFirstDataByDeviceIdOrderByTimestampDesc(deviceId).orElse(new Data());
//        return data.getData();
//    }


    private double recomputeDischargeEnergyWh(
            String deviceId,
            long fromEpochSec,
            long toEpochSec
    ) {
        if (toEpochSec <= fromEpochSec) return 0.0;

        Query query = new Query(
                Criteria.where("deviceId").is(deviceId)
                        .and("timestamp").gte(fromEpochSec).lte(toEpochSec)
        );
        query.with(Sort.by(Sort.Direction.ASC, "timestamp"));
        query.fields()
                .include("timestamp")
                .include("data.status")
                .include("data.power");

        List<Data> records = mongoTemplate.find(query, Data.class);
        if (records.size() < 2) return 0.0;

        double totalWh = 0.0;

        for (int i = 1; i < records.size(); i++) {
            Data prev = records.get(i - 1);
            Data curr = records.get(i);

            if (!"DISCHARGE".equals(String.valueOf(prev.getData().get("status"))))
                continue;

            Object p = prev.getData().get("power");
            if (p == null) continue;

            double power;
            try {
                power = Math.abs(Double.parseDouble(p.toString()));
            } catch (Exception e) {
                continue;
            }

            long delta = curr.getTimestamp() - prev.getTimestamp();
            if (delta <= 0) continue;

            if (delta > 3600) delta = 3600;

            totalWh += power * (delta / 3600.0);
        }

        return totalWh;
    }


    private static class EnergyCacheEntry {
        final double value;
        final long createdAt;

        EnergyCacheEntry(double value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, EnergyCacheEntry> energyCache =
            new ConcurrentHashMap<>();

    private static final long CACHE_TTL_MS = 2 * 60 * 1000;

    private double recomputeDischargeEnergyCached(
            String deviceId,
            long fromEpochSec,
            long toEpochSec,
            String cacheKey
    ) {
        EnergyCacheEntry cached = energyCache.get(cacheKey);
        if (cached != null &&
                (System.currentTimeMillis() - cached.createdAt) < CACHE_TTL_MS) {
            return cached.value;
        }

        double value = recomputeDischargeEnergyWh(deviceId, fromEpochSec, toEpochSec);
        energyCache.put(cacheKey, new EnergyCacheEntry(value));
        return value;
    }

    public EnergyStat getTodayStats(String deviceId) {

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zone);

        long todayStart = today.atStartOfDay(zone).toEpochSecond();
        long now = Instant.now().getEpochSecond();

        long secondsIntoDay = now - todayStart;

        long yesterdayStart = today.minusDays(1)
                .atStartOfDay(zone)
                .toEpochSecond();

        // ---- RECOMPUTE ENERGY (CACHED) ----
        String todayCacheKey =
                deviceId + ":today:" + LocalDate.now(zone) + ":" + (secondsIntoDay / 300);

        String yesterdayCacheKey =
                deviceId + ":yesterday:" + LocalDate.now(zone) + ":" + (secondsIntoDay / 300);

        double todayDischargeWh = recomputeDischargeEnergyCached(
                deviceId,
                todayStart,
                now,
                todayCacheKey
        );

        double yesterdayDischargeWh = recomputeDischargeEnergyCached(
                deviceId,
                yesterdayStart,
                yesterdayStart + secondsIntoDay,
                yesterdayCacheKey
        );

        double totalWhTrend = todayDischargeWh - yesterdayDischargeWh;

        // ---- LIVE HOURLY STATS (single scan, today only) ----
        Query query = new Query(
                Criteria.where("deviceId").is(deviceId)
                        .and("timestamp").gte(todayStart).lte(now)
        );
        query.with(Sort.by(Sort.Direction.ASC, "timestamp"));
        query.fields()
                .include("timestamp")
                .include("data.status")
                .include("data.power")
                .include("data.percent");

        List<Data> records = mongoTemplate.find(query, Data.class);

        Map<Integer, Double> dischargeHourly = new HashMap<>();
        Map<Integer, Double> chargeHourly = new HashMap<>();
        Map<Integer, Double> hourlyPercent = new TreeMap<>();

        double chargeTotalWh = 0;
        double percent = 0;

        if (!records.isEmpty()) {
            Object p = records.get(records.size() - 1).getData().get("percent");
            if (p != null) {
                try {
                    percent = Double.parseDouble(p.toString());
                } catch (Exception ignored) {
                }
            }
        }

        for (int i = 1; i < records.size(); i++) {

            Data prev = records.get(i - 1);
            Data curr = records.get(i);

            Object statusObj = prev.getData().get("status");
            Object powerObj = prev.getData().get("power");

            if (statusObj == null || powerObj == null) continue;

            double power;
            try {
                power = Math.abs(Double.parseDouble(powerObj.toString()));
            } catch (Exception e) {
                continue;
            }

            long delta = curr.getTimestamp() - prev.getTimestamp();
            if (delta <= 0 || delta > 3600) continue;

            double energyWh = power * (delta / 3600.0);

            int hour = Instant.ofEpochSecond(prev.getTimestamp())
                    .atZone(zone)
                    .getHour();

            Object percentObj = prev.getData().get("percent");
            if (percentObj != null) {
                try {
                    hourlyPercent.put(hour,
                            Double.parseDouble(percentObj.toString()));
                } catch (Exception ignored) {
                }
            }

            String status = statusObj.toString();

            if ("DISCHARGE".equals(status)) {
                dischargeHourly.merge(hour, energyWh, Double::sum);
            } else if ("CHARGING".equals(status)) {
                chargeTotalWh += energyWh;
                chargeHourly.merge(hour, energyWh, Double::sum);
            }
        }
        int currentHour = LocalDateTime.now(zone).getHour();
        if (dischargeHourly.size() > 2)
            dischargeHourly.remove(currentHour);
        if (chargeHourly.size() > 2)
            chargeHourly.remove(currentHour);
        if (hourlyPercent.size() > 2)
            hourlyPercent.remove(currentHour);

        // ---- PEAK / LOW / TRENDS ----
        double peakWh = 0;
        double lowestWh = 0;
        double peakTrend = 0;
        double lowestTrend = 0;

        if (!dischargeHourly.isEmpty()) {

            double sum = 0;
            peakWh = Double.MIN_VALUE;
            lowestWh = Double.MAX_VALUE;

            for (double hourlyWh : dischargeHourly.values()) {
//                System.err.println("hourly " + hourlyWh);
                sum += hourlyWh;
                peakWh = Math.max(peakWh, hourlyWh);
                lowestWh = Math.min(lowestWh, hourlyWh);
            }

            double avg = sum / dischargeHourly.size();
            peakTrend = peakWh - avg;
            lowestTrend = lowestWh - avg;

            if (peakWh == Double.MIN_VALUE) peakWh = 0;
            if (lowestWh == Double.MAX_VALUE) lowestWh = 0;
        }


        double percentTrend = 0;
        if (hourlyPercent.size() >= 2) {
            Iterator<Double> it = hourlyPercent.values().iterator();
            double prev = it.next(), curr = prev;
            while (it.hasNext()) {
                prev = curr;
                curr = it.next();
            }
            percentTrend = curr - prev;
        }

        EnergyStat stat = EnergyStat.builder()
                .deviceId(deviceId)
                .timestamp(now)
                .totalWh(round(todayDischargeWh))
                .totalWhTrend(round(totalWhTrend))
                .peakWh(round(peakWh))
                .lowestWh(round(lowestWh))
                .peakWhTrend(round(peakTrend))
                .lowestWhTrend(round(lowestTrend))
                .chargeTotalWh(round(chargeTotalWh))
                .percent(percent)
                .percentTrend(round(percentTrend))
                .updateDate(new Date())
                .build();

        // ---- DAILY UPSERT (rollup only) ----
        Query upsertQuery = new Query(
                Criteria.where("deviceId").is(deviceId)
                        .and("timestamp").gte(todayStart)
                        .lt(todayStart + 86400)
        );

        Update update = new Update()
                .set("totalWh", stat.getTotalWh())
                .set("peakWh", stat.getPeakWh())
                .set("lowestWh", stat.getLowestWh())
                .set("chargeTotalWh", stat.getChargeTotalWh())
                .set("totalWhTrend", stat.getTotalWhTrend())
                .set("peakWhTrend", stat.getPeakWhTrend())
                .set("lowestWhTrend", stat.getLowestWhTrend())
                .set("percent", percent)
                .set("percentTrend", stat.getPercentTrend())
                .set("updateDate", new Date())
                .setOnInsert("deviceId", deviceId)
                .setOnInsert("timestamp", todayStart);

        mongoTemplate.upsert(upsertQuery, update, EnergyStat.class);

        return stat;
    }


    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
