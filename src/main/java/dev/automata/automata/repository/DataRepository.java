package dev.automata.automata.repository;

import dev.automata.automata.dto.ChartDataAggregate;
import dev.automata.automata.model.Data;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public interface DataRepository extends MongoRepository<Data, String> {

    List<Data> findAllByDeviceId(String deviceId);

    @Nullable
    Data getFirstByDeviceIdOrderByTimestampDesc(String deviceId);

    List<Data> findByDeviceIdAndUpdateDateBetween(String deviceId, Date updateDate, Date updateDate2);

    Data findByDeviceId(String deviceId);

    Data getFirstDataByDeviceIdOrderByTimestampDesc(String deviceId);

    @Aggregation(pipeline = {
            "{ $match: { " +
                    "deviceId: ?0, " +  // Match by the provided deviceId
                    "updateDate: { $gte: ?1, $lte: ?2 } " +  // Match by updateDate range (startOfWeek to endOfWeek)
                    "}}",  // Match dateTime between the week start and end
            "{ $group: { " +
                    "_id: {dayOfWeek: { $dayOfWeek: '$updateDate' },}, " +  // Group by ISO week
                    "totalCurrent: { $sum: { $toDouble: '$data.current' } }, " +  // Summing the current
                    "totalPower: { $sum: { $toDouble: '$data.power' } }, " +  // Summing the power
                    "totalEnergy: { $sum: { $toDouble: '$data.totalEnergy' } }, " +  // Summing total energy
                    "deviceCount: { $sum: 1 }" +  // Count of devices (if needed)
                    "}}",
            "{ $sort: { _id: 1 } }"  // Sort by the ISO week number
    })
    List<Object> findEventsGroupedByDayOfWeek(String deviceId, Date startOfWeek, Date endOfWeek);
}