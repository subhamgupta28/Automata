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
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
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
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);
        chartDataDto.setPeriod(period);

        int week = period.equals("day") ? 0 : 6;

        var attrs = deviceChartsRepository.findByDeviceId(deviceId);
        var attributes = attributeRepository.findByDeviceIdAndTypeNot(deviceId, "DATA|AUX");

        List<Attribute> filteredAttributes = attributes.stream()
                .filter(attribute -> attrs.stream().anyMatch(attr -> attr.getAttributeKey().equals(attribute.getKey()) && attr.isShowChart()))
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        Date startDate = Date.from(now.minusHours(7).atZone(ZoneId.systemDefault()).withMinute(0).withSecond(0).toInstant());
        Date endDate = Date.from(now.atZone(ZoneId.systemDefault()).withHour(23).withMinute(59).withSecond(59).toInstant());

        if (!filteredAttributes.isEmpty()) {
            var attr = filteredAttributes.stream().filter(s -> s.getKey().equals(attributeKey)).findFirst();
            chartDataDto.setAttributes(filteredAttributes.stream().map(Attribute::getKey).collect(Collectors.toList()));
            if (attr.isEmpty()) {
                chartDataDto.setMessage("No attribute found for key: " + attributeKey + ". Use one of these to fetch charts.");
                return chartDataDto;
            }
            chartDataDto.setUnit(attr.get().getUnits());
        }

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
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);

        List<Object> finalResultList = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object object : results.getMappedResults()) {
            var map = (Map<String, Object>) object;
            map.forEach((key, value) -> {
                if (key.startsWith("net")) {
                    map.put(key, Double.parseDouble(String.format("%.2f", Math.abs(Double.parseDouble(value.toString())))));
                }
                if (key.startsWith("startOfDay")) {
                    labels.add(value.toString());
                }
            });
            finalResultList.add(map);
        }

        chartDataDto.setTimestamps(labels);
        chartDataDto.setLabel(attributeKey);
        chartDataDto.setData(finalResultList);
        chartDataDto.setDataKey("net" + attributeKey);
        return chartDataDto;
    }

    public ChartDataDto getPieChartData(String deviceId, String period) {
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);
        chartDataDto.setPeriod(period);

        var keyMap = new HashMap<String, String>();

        var attrs = deviceChartsRepository.findByDeviceId(deviceId);
        var attributes = attributeRepository.findByDeviceIdAndTypeNot(deviceId, "DATA|AUX");

        List<Attribute> filteredAttributes = attributes.stream()
                .filter(attribute -> attrs.stream().anyMatch(attr -> attr.getAttributeKey().equals(attribute.getKey()) && attr.isShowChart()))
                .toList();

        LocalDateTime now = LocalDateTime.now();

        chartDataDto.setAttributes(filteredAttributes.stream().map(Attribute::getKey).collect(Collectors.toList()));


        Date startDate = Date.from(now.atZone(ZoneId.systemDefault()).withHour(0).withMinute(0).withSecond(0).toInstant());
        Date endDate = Date.from(now.atZone(ZoneId.systemDefault()).withHour(23).withMinute(59).withSecond(59).toInstant());

        System.err.println(startDate);
        System.err.println(endDate);

        var match = match(where("deviceId").is(deviceId)
                .and("updateDate").gte(startDate).lte(endDate));
        var project = project("deviceId", "data", "updateDate")
                .and(
                        DateOperators.dateOf("updateDate").toString("%m-%d")
                                .withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))
                ).as("dateDay")
                .and(
                        DateOperators.dateOf("updateDate").toString("%m-%d %H:%M")
                                .withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))
                ).as("dateShow");

        for (Attribute attribute : filteredAttributes) {
            keyMap.put(attribute.getKey(), attribute.getDisplayName());
            project = project.andExpression("toDouble(data." + attribute.getKey() + ")")
                    .as(attribute.getKey());
        }

        var group = group("dateDay")
                .count().as("count")
                .max("dateShow").as("endOfDay")
                .min("dateShow").as("startOfDay");

        for (Attribute attribute : filteredAttributes) {
            group = group.avg(attribute.getKey()).as("net" + attribute.getKey());
        }

        var sort = sort(Sort.by(Sort.Order.asc("startOfDay")));


        Aggregation aggregation = newAggregation(match, project, group, sort);
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);

        System.err.println(results.getMappedResults());
        List<Object> finalResultList = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object object : results.getMappedResults()) {
            System.err.println(object);
            var map = (Map<String, Object>) object;
            var newMap = new HashMap<String, Object>();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                var key = entry.getKey();
                var value = entry.getValue();
                System.err.println(key);
                if (key.startsWith("net")) {
                    newMap.put(key.replace("net", ""), Double.parseDouble(String.format("%.1f", Math.abs(Double.parseDouble(value.toString())))));
                    labels.add(keyMap.get(key.replace("net", "")));
                }else{
                    newMap.put(key, value);
                }
            }

//            map.forEach((key, value) -> {
//                if (key.startsWith("net")) {
//                    map.put(key.substring(3), Double.parseDouble(String.format("%.1f", Math.abs(Double.parseDouble(value.toString())))));
//                    labels.add(keyMap.get(key.replace("net", "")));
//                }
////                if (key.startsWith("startOfDay")) {
////                    labels.add(value.toString());
////                }
//            });
            finalResultList.add(newMap);
        }

        chartDataDto.setTimestamps(labels);
        chartDataDto.setData(finalResultList);
        chartDataDto.setLabel("");
        return chartDataDto;
    }
}