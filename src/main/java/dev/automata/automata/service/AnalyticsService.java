package dev.automata.automata.service;

import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Data;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.DateOperators.Timezone.fromOffset;
import static org.springframework.data.mongodb.core.aggregation.Fields.field;
import static org.springframework.data.mongodb.core.aggregation.Fields.from;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceRepository deviceRepository;
    private final MongoTemplate mongoTemplate;


//    public ChartDataDto getChartData(String deviceId, String attributeKey) {
//        ChartDataDto chartDataDto = new ChartDataDto();
//        chartDataDto.setDeviceId(deviceId);
//        var attributes = attributeRepository.findByDeviceIdAndType(deviceId, "%");
//        LocalDate today = LocalDate.now();
//
//        if (!attributes.isEmpty()) {
//            var attr = attributes.stream().filter(s -> s.getKey().equals(attributeKey)).toList();
//            System.err.println(attr);
//            chartDataDto.setUnit(attr.getFirst().getUnits());
//        }
//    }


    public ChartDataDto getChartData2(String deviceId, String attributeKey, String period) {
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);

        int week = 6;
        if (period.equals("day")) {
            week = 0;
        }
        chartDataDto.setPeriod(period);

        var attributes = attributeRepository.findByDeviceIdAndTypeNot(deviceId, "DATA|AUX");

        LocalDateTime now = LocalDateTime.now();

        // Calculate the start of the last 24 hours (24 hours ago)
        LocalDateTime startOfLast24Hours = now.minusHours(11);

        // Convert to Date
        Date startDate = Date.from(startOfLast24Hours.atZone(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());


        if (!attributes.isEmpty()) {
            String finalAttributeKey = attributeKey;
            var attr = attributes.stream().filter(s -> s.getKey().equals(finalAttributeKey)).toList();
            chartDataDto.setAttributes(attributes.stream().map(Attribute::getKey).collect(Collectors.toList()));
            if (attr.isEmpty()){
                chartDataDto.setMessage("No attribute found for key: " + attributeKey+". Use one of these to fetch charts.");
                chartDataDto.setData(new ArrayList<>());
                chartDataDto.setDataKey("");
                chartDataDto.setUnit("");
                chartDataDto.setLabel("");
                return chartDataDto;
            }
            System.err.println(attr);
            chartDataDto.setUnit(attr.getFirst().getUnits());
        }


        // Step 1: Match stage (filter by deviceId and date range)
        var match = match(where("deviceId").is(deviceId)
                .and("updateDate").gte(startDate).lte(endDate));


        var projectBuilder = project(
                from(
                        field("deviceId"),
                        field("data"),
                        field("updateDate"),
                        field("dateTime", "day")
                ))
                .and(
                        DateOperators.dateOf("updateDate")
                                .toString("%m-%d %H")
                                .withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))
                ).as("dateDay")
                .and(
                        DateOperators.dateOf("updateDate")
                                .toString("%m-%d %H:%M")
                                .withTimezone(DateOperators.Timezone.valueOf("Asia/Calcutta"))
                ).as("dateShow");
        /*
        "%Y-%m-%dT%H:%M:%S.%LZ"
        * */


        projectBuilder = projectBuilder.andExpression("toDouble(data." + attributeKey + ")").as("t" + attributeKey);


        // Step 3: Group by day and apply sum and other operations for each dynamic key
        var groupBuilder = Aggregation.group("dateDay")
                .count().as("count")
                .max("dateShow").as("endOfDay")
                .min("dateShow").as("startOfDay");


        // Dynamically sum up values for each key

        groupBuilder = groupBuilder.avg("t" + attributeKey).as("net" + attributeKey);
//                .push("t" + attributeKey).as("nC" + attributeKey);
        var sort = Aggregation.sort(Sort.by(Sort.Order.asc("startOfDay")));


        Aggregation aggregation = newAggregation(match, projectBuilder, groupBuilder, sort);
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);


        List<Object> resultList = results.getMappedResults();
        List<Object> finalResultList = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object object : resultList) {
            try {
                var map = (Map<String, Object>) object;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("nC")) {
                        var list = (List<Object>) entry.getValue();
                        var val = list.stream().map(d->Math.abs(Double.parseDouble(d.toString()))).toList();
                        map.put(entry.getKey(), val);
                    }
                    if (entry.getKey().startsWith("net")) {
                        var d = Double.parseDouble(entry.getValue().toString());
                        map.put(entry.getKey(), Double.parseDouble(String.format("%.2f", Math.abs(d))));
                    }
                    if (entry.getKey().startsWith("startOfDay")) {
                        labels.add(entry.getValue().toString());
                    }
                }
                finalResultList.add(map);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }

        chartDataDto.setTimestamps(labels);
        chartDataDto.setLabel(attributeKey);
        attributeKey = "net" + attributeKey;
        chartDataDto.setData(finalResultList);
        chartDataDto.setDataKey(attributeKey);
//        chartDataDto.setPeriod(startOfWeek + " to " + endOfWeek);
        return chartDataDto;
    }


}
