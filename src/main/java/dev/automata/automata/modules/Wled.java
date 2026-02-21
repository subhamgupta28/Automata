package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.DeviceActionState;
import dev.automata.automata.model.Status;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.http.*;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class Wled {
    private final String ipAddress;
    private String deviceTopic = "wled/all";
    private static final HashMap<String, Object> lastState = new HashMap<>();
    private final MessageChannel mqttOutboundChannel;

    public String setRGBHexColor(String color) {
//        System.err.println("Color " + color);
//        sendToTopic(deviceTopic + "/col", color);
        sendToTopic(deviceTopic + "/api", "FX=0&CL="+color);
        return "success";
    }

    public String setRGB(int r, int g, int b) {
        validate(r);
        validate(g);
        validate(b);
//        sendToTopic(deviceTopic + "/col", String.format("#%02X%02X%02X", r, g, b));
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static void validate(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Color values must be 0-255");
        }
    }


    private void sendToTopic(String topic, String payload) {
        try {
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader("mqtt_topic", topic)
                            .build()
            );
            System.out.println("📤 Sent to " + topic + " => " + payload);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public Wled(String ipAddress, MessageChannel mqttOutboundChannel, Device device) {
        this.ipAddress = ipAddress + "/json/state";
        this.mqttOutboundChannel = mqttOutboundChannel;
        if (device != null) {
            var configs = device.getAttributes().stream().filter(f -> f.getType().equals("ACTION|CONFIG")).toList();
//            System.err.println(configs);
            if (!configs.isEmpty()) {
                deviceTopic = configs.getFirst().getExtras().get("deviceTopic").toString();
            }
        }

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
                .name("Light Strip")
                .sleep(false)
                .reboot(false)
                .host("bigled")
                .macAddr("8C:A3:99:CF:FB:AC")
                .accessUrl("http://192.168.1.55")
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
                                        .key("color")
                                        .displayName("Color")
                                        .type("ACTION|COLOR")
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
                                        .build(),
                                Attribute.builder()
                                        .key("config")
                                        .displayName("Config")
                                        .type("ACTION|CONFIG")
                                        .units("")
                                        .extras(Map.of("deviceTopic", "wled/bigled"))
                                        .visible(true)
                                        .build()

                        )
                )
                .build();

    }

    public Map<String, Object> getInfo(String deviceId, DeviceActionState deviceState) {
        var res = new RestTemplate().getForObject(ipAddress, WledResponse.class);
//        System.err.println(res);
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
                var col = res.seg.getFirst().get("col");
//                System.err.println(col);
                List<?> c = (List<?>) col;
                List<?> first = (List<?>) c.get(0);

                int r = (int) first.get(0);
                int g = (int) first.get(1);
                int b = (int) first.get(2);
                if (deviceState != null)
                    lastState.putAll(deviceState.getPayload());
                lastState.put("onOff", res.on);
                lastState.put("bright", res.bri);
                lastState.put("presets", res.ps);
                lastState.put("device_id", deviceId);
                lastState.put("color", setRGB(r, g, b));
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
        sendToTopic(deviceTopic, "T");
        try {
            var r = """
                    {"on": "t"}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r, Object.class);
//            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> powerOnOff(boolean on) {
        lastState.put("onOff", on);
        sendToTopic(deviceTopic, String.valueOf(on));
        try {
            var r = """
                    {"on": v}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r.replace("v", String.valueOf(on)), Object.class);
//            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> setBrightness(int brightness) {
        lastState.put("bright", brightness);
        sendToTopic(deviceTopic, String.valueOf(brightness));
        try {
            var r = """
                    {"bri": v}
                    """;

            var response = new RestTemplate().postForObject(ipAddress, r.replace("v", String.valueOf(brightness)), Object.class);
//            System.err.println(response);
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
//            System.err.println(response);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

//    public String setRGB(int red, int green, int blue) {
//        return new RestTemplate().getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
//    }

    public String setEffect(int effect) {
        return new RestTemplate().getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
