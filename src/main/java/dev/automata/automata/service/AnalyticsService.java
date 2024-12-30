package dev.automata.automata.service;

import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceChartsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceChartsRepository deviceChartsRepository;
    private final MongoTemplate mongoTemplate;

    public ChartDataDto getChartData2(String deviceId, String attributeKey, String period) {
        // Initialize ChartData
        ChartDataDto chartDataDto = initializeChartDataDto(deviceId, period);

        // Retrieve and filter attributes
        List<Attribute> filteredAttributes = getFilteredAttributes(deviceId);
        chartDataDto.setAttributes(filteredAttributes.stream().map(Attribute::getKey).collect(Collectors.toList()));

        // Validate target attribute
        Optional<Attribute> targetAttribute = filteredAttributes.stream()
                .filter(attribute -> attribute.getKey().equals(attributeKey))
                .findFirst();
        if (targetAttribute.isEmpty()) {
            chartDataDto.setMessage("No attribute found for key: " + attributeKey + ". Use one of these to fetch charts.");
            return chartDataDto;
        }
        chartDataDto.setUnit(targetAttribute.get().getUnits());

        // Fetch and process aggregation results
        List<Object> aggregatedResults = executeAggregation(
                deviceId,
                getStartDate(period),
                getEndDate(),
                attributeKey
        );

        // Map results to desired output
        List<String> timestamps = new ArrayList<>();
        List<Object> processedResults = processChartData(aggregatedResults, timestamps, "net" + attributeKey);

        // Update ChartDataDto
        chartDataDto.setTimestamps(timestamps);
        chartDataDto.setLabel(attributeKey);
        chartDataDto.setData(processedResults);
        chartDataDto.setDataKey("net" + attributeKey);

        return chartDataDto;
    }

    public ChartDataDto getPieChartData(String deviceId, String period) {
        // Initialize ChartData
        ChartDataDto chartDataDto = initializeChartDataDto(deviceId, period);

        // Retrieve and filter attributes
        List<Attribute> filteredAttributes = getFilteredAttributes(deviceId);
        chartDataDto.setAttributes(filteredAttributes.stream().map(Attribute::getKey).collect(Collectors.toList()));

        // Create a mapping of attribute keys to display names
        Map<String, String> keyToDisplayNameMap = filteredAttributes.stream()
                .collect(Collectors.toMap(Attribute::getKey, Attribute::getDisplayName));

        // Fetch and process aggregation results
        List<Object> aggregatedResults = executePieChartAggregation(
                deviceId,
                getStartDate(period),
                getEndDate(),
                filteredAttributes
        );

        // Map results to desired output
        List<String> timestamps = new ArrayList<>();
        List<Object> processedResults = processPieChartData(aggregatedResults, keyToDisplayNameMap, timestamps);

        // Update ChartDataDto
        chartDataDto.setTimestamps(timestamps);
        chartDataDto.setData(processedResults);
        return chartDataDto;
    }

    private ChartDataDto initializeChartDataDto(String deviceId, String period) {
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);
        chartDataDto.setPeriod(period);
        return chartDataDto;
    }

    private List<Attribute> getFilteredAttributes(String deviceId) {
        var attrs = deviceChartsRepository.findByDeviceId(deviceId);
        var attributes = attributeRepository.findByDeviceIdAndTypeNot(deviceId, "DATA|AUX");

        return attributes.stream()
                .filter(attribute -> attrs.stream()
                        .anyMatch(attr -> attr.getAttributeKey().equals(attribute.getKey()) && attr.isShowChart()))
                .collect(Collectors.toList());
    }

    private Date getStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        if ("day".equalsIgnoreCase(period)) {
            return Date.from(now.minusHours(7).atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).toInstant());
        }
        // More cases for "week", "month", etc., can be handled here
        return Date.from(now.atZone(ZoneId.systemDefault()).withHour(0).withMinute(0).withSecond(0).toInstant());
    }

    private Date getEndDate() {
        LocalDateTime now = LocalDateTime.now();
        return Date.from(now.atZone(ZoneId.systemDefault()).withHour(23).withMinute(59).withSecond(59).toInstant());
    }

    private List<Object> executeAggregation(String deviceId, Date startDate, Date endDate, String attributeKey) {
        var match = match(where("deviceId").is(deviceId).and("updateDate").gte(startDate).lte(endDate));
        var project = project("deviceId", "data", "updateDate")
                .and(DateOperators.dateOf("updateDate").toString("%m-%d %H").withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))).as("dateDay")
                .and(DateOperators.dateOf("updateDate").toString("%m-%d %H:%M").withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))).as("dateShow")
                .andExpression("toDouble(data." + attributeKey + ")").as("t" + attributeKey);

        var group = group("dateDay")
                .count().as("count")
                .max("dateShow").as("endOfDay")
                .min("dateShow").as("startOfDay")
                .avg("t" + attributeKey).as("net" + attributeKey);

        var sort = sort(Sort.by(Sort.Order.asc("startOfDay")));

        Aggregation aggregation = newAggregation(match, project, group, sort);
        return mongoTemplate.aggregate(aggregation, Data.class, Object.class).getMappedResults();
    }

    private List<Object> executePieChartAggregation(String deviceId, Date startDate, Date endDate, List<Attribute> attributes) {
        var match = match(where("deviceId").is(deviceId).and("updateDate").gte(startDate).lte(endDate));
        var project = project("deviceId", "data", "updateDate")
                .and(DateOperators.dateOf("updateDate").toString("%m-%d").withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))).as("dateDay")
                .and(DateOperators.dateOf("updateDate").toString("%m-%d %H:%M").withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))).as("dateShow");

        for (Attribute attribute : attributes) {
            project = project.andExpression("toDouble(data." + attribute.getKey() + ")").as(attribute.getKey());
        }

        var group = group("dateDay")
                .count().as("count")
                .max("dateShow").as("endOfDay")
                .min("dateShow").as("startOfDay");

        for (Attribute attribute : attributes) {
            group = group.avg(attribute.getKey()).as("net" + attribute.getKey());
        }

        var sort = sort(Sort.by(Sort.Order.asc("startOfDay")));

        Aggregation aggregation = newAggregation(match, project, group, sort);
        return mongoTemplate.aggregate(aggregation, Data.class, Object.class).getMappedResults();
    }

    private List<Object> processChartData(List<Object> results, List<String> timestamps, String dataKeyPrefix) {
        List<Object> finalResultList = new ArrayList<>();
        for (Object object : results) {
            var map = (Map<String, Object>) object;
            map.forEach((key, value) -> {
                if (key.startsWith(dataKeyPrefix)) {
                    map.put(key, Double.parseDouble(String.format("%.2f", Math.abs(Double.parseDouble(value.toString())))));
                }
                if (key.startsWith("startOfDay")) {
                    timestamps.add(value.toString());
                }
            });
            finalResultList.add(map);
        }
        return finalResultList;
    }

    private List<Object> processPieChartData(List<Object> results, Map<String, String> keyMap, List<String> labels) {
        List<Object> finalResultList = new ArrayList<>();
        for (Object result : results) {
            var map = (Map<String, Object>) result;
            var processedMap = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.startsWith("net")) {
                    processedMap.put(key.replace("net", ""), Double.parseDouble(String.format("%.1f", Math.abs(Double.parseDouble(value.toString())))));
                    labels.add(keyMap.get(key.replace("net", "")));
                } else {
                    processedMap.put(key, value);
                }
            }
            finalResultList.add(processedMap);
        }
        return finalResultList;
    }
}