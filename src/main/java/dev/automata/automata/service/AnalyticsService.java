package dev.automata.automata.service;

import dev.automata.automata.dto.ChartDataAggregate;
import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Projections.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceRepository deviceRepository;


    private final MongoTemplate mongoTemplate;


    public ChartDataDto getChartData(String deviceId, String attributeKey) {

        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);

        var attributes = attributeRepository.findByDeviceIdAndType(deviceId, "DATA|CHART");
        System.err.println(attributes);
        LocalDate now = LocalDate.now();
        LocalDate startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        Date startDate = Date.from(startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(endOfWeek.atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant());

        var key1 = attributes.get(0).getKey();
        var key2 = attributes.get(1).getKey();
        var key3 = attributes.get(2).getKey();
        var key4 = attributes.get(3).getKey();


        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("deviceId").is(deviceId).and("updateDate").gte(startDate).lte(endDate)),
                Aggregation.project("deviceId", "data", "updateDate")
                        .andExpression("toDouble(data." + key1 + ")").as("t" + key1)
                        .andExpression("toDouble(data." + key2 + ")").as("t" + key2)
                        .andExpression("dateToString('%Y-%m-%d', updateDate)").as("day"),
                Aggregation.group("day")
                        .count().as("count")
                        .max("updateDate").as("endOfDay")
                        .min("updateDate").as("startOfDay")
                        .sum("t" + key1).as("net" + key1)
                        .sum("t" + key2).as("net" + key2)
        );


        // Execute the aggregation query
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);


//        List<ChartDataAggregate> result = results.getMappedResults();

        System.err.println(results.getMappedResults());
//        System.err.println(results.getMappedResults());

        chartDataDto.setDataKey("net" + key1);
        chartDataDto.setData(results.getMappedResults());
        chartDataDto.setPeriod(startOfWeek + " to " + endOfWeek);


        return chartDataDto;
    }

    public ChartDataDto getChartData2(String deviceId, String attributeKey) {
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);

        // Fetch attribute keys dynamically based on the device and type
        var attributes = attributeRepository.findByDeviceIdAndType(deviceId, "DATA|CHART");
        System.err.println(attributes);

        // Determine the date range (start of week to end of week)
        LocalDate now = LocalDate.now();
        LocalDate startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        Date startDate = Date.from(startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(endOfWeek.atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant());

        // Collect keys dynamically from the attributes
        List<String> keys = attributes.stream()
                .map(Attribute::getKey)
                .toList();

        System.err.println(keys);


        // Step 1: Match stage (filter by deviceId and date range)
        var match = Aggregation.match(Criteria.where("deviceId").is(deviceId)
                .and("updateDate").gte(startDate).lte(endDate));

        // Step 2: Project the fields dynamically based on the keys
        var projectBuilder = Aggregation.project("deviceId", "data", "updateDate")
                .andExpression("dateToString('%Y-%m-%d', updateDate)").as("day");
        for (String key : keys) {
            projectBuilder = projectBuilder.andExpression("toDouble(data." + key + ")").as("t" + key);
        }

        // Step 3: Group by day and apply sum and other operations for each dynamic key
        var groupBuilder = Aggregation.group("day")
                .count().as("count")

                .max("updateDate").as("endOfDay")
                .min("updateDate").as("startOfDay");

        // Dynamically sum up values for each key
        for (String key : keys) {
            attributeKey = "net" + key;
            groupBuilder = groupBuilder.sum("t" + key).as("net" + key).push("t" + key).as("n" + key);
        }

        // Create the Aggregation object
        Aggregation aggregation = Aggregation.newAggregation(match, projectBuilder, groupBuilder);

        // Execute the aggregation query
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);

        // Print the results for debugging
//        System.err.println(results.getMappedResults());

        // Map the results to the DTO
        List<Object> resultList = results.getMappedResults();


        for (Object object : resultList) {
            try {
                var map = (Map<String, Object>) object;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("nC")){
                        System.err.println("keys: " + entry.getKey());
                        var res = calculateEnergyForDay((Date) map.get("startOfDay"), (Date) map.get("endOfDay"), (List<Double>) map.get(entry.getKey()));
                        System.err.println("po" + res);

                    }


                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        chartDataDto.setData(resultList);

        // Set dynamic data key (e.g., netCurrent, netPower, etc.)
        chartDataDto.setDataKey(attributeKey);

        // Set period range (e.g., week range)
        chartDataDto.setPeriod(startOfWeek + " to " + endOfWeek);

        return chartDataDto;
    }

    public static double calculateEnergyForDay(Date startTime, Date endTime, List<Double> powerReadings) {
        // Convert java.util.Date to Instant and then to OffsetDateTime
        Instant startInstant = startTime.toInstant();
        Instant endInstant = endTime.toInstant();

        // Convert Instant to OffsetDateTime (using UTC ZoneOffset for simplicity)
        OffsetDateTime startDate = OffsetDateTime.ofInstant(startInstant, ZoneOffset.UTC);
        OffsetDateTime endDate = OffsetDateTime.ofInstant(endInstant, ZoneOffset.UTC);

        // Calculate the duration between start and end times
        Duration duration = Duration.between(startDate, endDate);

        // Calculate the number of 3-minute intervals
        long totalMinutes = duration.toMinutes();
        int intervals = (int) (totalMinutes / 3);  // Each reading is 3 minutes apart
        System.err.println("intervals: " + intervals);
        System.err.println("powerReadings: " + powerReadings.size());


        // Calculate the total energy
        double totalEnergy = 0;
        for (int i = 0; i < powerReadings.size(); i++) {
            double power = powerReadings.get(i);  // Power reading at this 3-minute interval
            totalEnergy += power * (3.0 / 60.0);  // Energy = Power * time (3 minutes = 3/60 hours)
        }

        return totalEnergy;
    }


}
