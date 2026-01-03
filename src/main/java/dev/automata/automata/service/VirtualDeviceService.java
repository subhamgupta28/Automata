package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.Dashboard;
import dev.automata.automata.model.VirtualDevice;
import dev.automata.automata.repository.VirtualDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VirtualDeviceService {

    private final VirtualDeviceRepository virtualDeviceRepository;
    private final NotificationService notificationService;

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
        if (device==null)
            return "Device not found.";
        device.setX(Math.floor(Double.parseDouble(x)));
        device.setY(Math.floor(Double.parseDouble(y)));
        device.setWidth(Math.floor(Double.parseDouble(width)));
        device.setHeight(Math.floor(Double.parseDouble(height)));

        virtualDeviceRepository.save(device);
        notificationService.sendNotification("Devices positions updated", "success");
        return "success";
    }
}
