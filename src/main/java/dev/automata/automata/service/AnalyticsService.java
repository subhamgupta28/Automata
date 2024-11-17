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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceRepository deviceRepository;
    private final MongoTemplate mongoTemplate;


    public ChartDataDto getChartData2(String deviceId, String attributeKey) {
        ChartDataDto chartDataDto = new ChartDataDto();
        chartDataDto.setDeviceId(deviceId);

        var attributes = attributeRepository.findByDeviceIdAndType(deviceId, "DATA|CHART");

        LocalDate endOfWeek = LocalDate.now().plusDays(1);
        LocalDate startOfWeek = endOfWeek.minusDays(6);
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+5:30"));

        Date startDate = Date.from(startOfWeek.atStartOfDay(ZoneId.of("UTC")).toInstant());
        Date endDate = Date.from(endOfWeek.atTime(23, 59, 59, 999999999).atZone(ZoneId.of("UTC")).toInstant());

        System.err.println(startOfWeek);
        System.err.println(endOfWeek);

        // Collect keys dynamically from the attributes
        List<String> keys = attributes.stream()
                .map(Attribute::getKey)
                .toList();

        // Step 1: Match stage (filter by deviceId and date range)
        var match = Aggregation.match(Criteria.where("deviceId").is(deviceId)
                .and("updateDate").gte(startDate));

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
            groupBuilder = groupBuilder.sum("t" + key).as("net" + key)
                    .push("t" + key).as("n" + key);
        }
        var sort = Aggregation.sort(Sort.by(Sort.Order.asc("startOfDay")));


        Aggregation aggregation = Aggregation.newAggregation(match, projectBuilder, groupBuilder, sort);
        AggregationResults<Object> results = mongoTemplate.aggregate(aggregation, Data.class, Object.class);


        List<Object> resultList = results.getMappedResults();
        List<Object> finalResultList = new ArrayList<>();
        for (Object object : resultList) {
            try {
                var map = (Map<String, Object>) object;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("nC")){
                        var list = (List<Object>) entry.getValue();
                        var val = list.stream().mapToDouble(d->Math.abs(Double.parseDouble(d.toString())));
                        map.put(entry.getKey(), val);
                    }
                    if (entry.getKey().startsWith("net")){
                        var d = Double.parseDouble(entry.getValue().toString());
                        map.put(entry.getKey(), Math.ceil(Math.abs(d)));
                    }
                }
                finalResultList.add(map);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        chartDataDto.setData(finalResultList);
        chartDataDto.setDataKey(attributeKey);
        chartDataDto.setPeriod(startOfWeek + " to " + endOfWeek);
        return chartDataDto;
    }


}
