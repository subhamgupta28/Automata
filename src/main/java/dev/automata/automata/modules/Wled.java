package dev.automata.automata.modules;

import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.DeviceActionState;
import dev.automata.automata.model.Status;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class Wled {
    private final String ipAddress;
    private static final HashMap<String, Object> lastState = new HashMap<>();


    public Wled(String ipAddress) {
        this.ipAddress = ipAddress + "/json/state";
    }
/// [
///{
///     "macAddr": "8C:A3:99:CF:FB:SG"
///   },
///   {
///     "macAddr": "DC:54:75:EE:0F:7C"
///   },
///   {
///     "macAddr": "DC:54:75:EB:6C:F4"
///   }
/// ]

    public RegisterDevice newDevice() {
        return RegisterDevice.builder()
                .name("Matrix")
                .sleep(false)
                .reboot(false)
                .host("matrix")
                .macAddr("8C:A3:99:CF:FB:SG")
                .accessUrl("http://192.168.1.65")
                .type("WLED")
                .status(Status.ONLINE)
                .updateInterval(190000L)
                .attributes(
                        List.of(
                                Attribute.builder()
                                        .key("onOff")
                                        .displayName("On Off")
                                        .type("ACTION|SWITCH")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("toggle")
                                        .displayName("Toggle")
                                        .type("ACTION|OUT")
                                        .units("")
                                        .extras(new HashMap<>())
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("preset")
                                        .displayName("Presets")
                                        .type("ACTION|PRESET")
                                        .units("")
                                        .extras(Map.of("p1", 1, "p2", 2, "p3", 3, "p4", 4, "p5", 5))
                                        .visible(true)
                                        .build(),
                                Attribute.builder()
                                        .key("bright")
                                        .displayName("Brightness")
                                        .type("ACTION|SLIDER")
                                        .units("")
                                        .extras(Map.of("min", 0, "max", 255))
                                        .visible(true)
                                        .build()

                        )
                )
                .build();

    }

    public Map<String, Object> getInfo(String deviceId, DeviceActionState deviceState) {
        var res = new RestTemplate().getForObject(ipAddress, WledResponse.class);
        if (res != null) {
            try {
//                // Convert the XML string into a byte stream
//                ByteArrayInputStream input = new ByteArrayInputStream(res.getBytes());
//
//                // Initialize the XML Document Builder
//                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//                DocumentBuilder builder = factory.newDocumentBuilder();
//                Document doc = builder.parse(input);
//
//                // Normalize the document structure
//                doc.getDocumentElement().normalize();

//                boolean onOff = getTagValue(doc, "ac") > 0;
//                int bright = getTagValue(doc, "ac");

                if (deviceState != null)
                    lastState.putAll(deviceState.getPayload());
                lastState.put("onOff", res.on);
                lastState.put("bright", res.bri);
                lastState.put("presets", res.ps);
                lastState.put("device_id", deviceId);
                return lastState;
                // Get the root element
            } catch (Exception e) {
                System.err.println(e);
            }

        }
        return lastState;
    }

    private int getTagValue(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return Integer.parseInt(nodeList.item(0).getTextContent()); // Return the text content of the first matching element
        }
        return -1; // Return null if the tag is not found
    }

    @Async
    public CompletableFuture<String> toggleOnOff() {
        try {
            var r = """
                    {"on": "t"}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r, Object.class);
            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> powerOnOff(boolean on) {
        lastState.put("onOff", on);

        try {
            var r = """
                    {"on": v}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r.replace("v", String.valueOf(on)), Object.class);
            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> setBrightness(int brightness) {
        lastState.put("bright", brightness);
        try {
            var r = """
                    {"bri": v}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r.replace("v", String.valueOf(brightness)), Object.class);
            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }

    }

    @Async
    public CompletableFuture<String> setPresets(int presets) {
        try {
            String payload = String.format("{\"ps\": %d}", presets);
            var response = new RestTemplate().postForObject(ipAddress, payload, Object.class);
            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    public String setRGB(int red, int green, int blue) {
        return new RestTemplate().getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
    }

    public String setEffect(int effect) {
        return new RestTemplate().getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
