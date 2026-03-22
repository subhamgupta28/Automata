package dev.automata.automata.modules;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.dto.WledXmlResponse;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import org.springframework.http.*;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class Wled {
    private String deviceTopic = "automata-wled/all/";
    private final MessageChannel mqttOutboundChannel;

    public String setRGBHexColor(String color, String key) {
        var payload = switch (key) {
            //FX=0&
            case "color1" -> "CL=";
            case "color2" -> "C2=";
            case "color3" -> "C3=";
            default -> "FX=0";
        };
        sendToTopic(deviceTopic + "/api", payload + color);
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
//            System.out.println("📤 Sent to " + topic + " => " + payload);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public Wled(MessageChannel mqttOutboundChannel, Device device) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        if (device != null) {
            var configs = device.getAttributes().stream().filter(f -> f.getType().equals("ACTION|CONFIG")).toList();
//            System.err.println(configs);
            if (!configs.isEmpty()) {
                deviceTopic = configs.getFirst().getExtras().get("deviceTopic").toString();
            }
        }

    }

    public WledResponse parseWledXml(String payload) {

        try {
            XmlMapper mapper = new XmlMapper();
            WledXmlResponse xml = mapper.readValue(payload, WledXmlResponse.class);
            WledResponse res = new WledResponse();

            res.on = xml.ac > 0;
            res.bri = xml.ac;
            res.ps = xml.ps;

            Map<String, Object> seg = new HashMap<>();
            seg.put("fx", xml.fx);
            seg.put("sx", xml.sx);
            seg.put("ix", xml.ix);
            seg.put("col", List.of(xml.cl, xml.cs));

            res.seg = List.of(seg);

            return res;
        } catch (Exception e) {
            System.err.println(e);
        }
        return null;
    }

    public String handleAction(Map<String, Object> input) {
        try {
            System.err.println("INPUT: " + input);
            StringBuilder payload = new StringBuilder();

            for (var entry : input.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                switch (key) {
                    case "bright" -> append(payload, "A=" + value);
                    case "onOff" -> {
                        int v = value.toString().equals("true") ? 1
                                : value.toString().equals("false") ? 0
                                : 2;
                        append(payload, "T=" + v);
                    }
                    case "toggle" -> append(payload, "T");
                    case "color1" -> append(payload, "CL=" + value);
                    case "color2" -> append(payload, "C2=" + value);
                    case "preset", "presets" -> append(payload, "FX=" + value);
                }
            }

            sendToTopic(deviceTopic + "/api", payload.toString());

        } catch (Exception e) {
            System.err.println(e);
            return null;
        }
        return "success";
    }

    private void append(StringBuilder sb, String value) {
        if (!sb.isEmpty()) {
            sb.append("&");
        }
        sb.append(value);
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

    public Map<String, Object> convertToMap(WledResponse res, String deviceId) {
        var lastState = new HashMap<String, Object>();
        if (res != null) {
            try {

                var col = res.seg.getFirst().get("col");
                List<?> c = (List<?>) col;
                List<?> first = (List<?>) c.getFirst();
                int r = (int) first.get(0);
                int g = (int) first.get(1);
                int b = (int) first.get(2);

                if (c.size() > 1) {
                    List<?> second = (List<?>) c.get(1);
                    int r2 = (int) second.get(0);
                    int g2 = (int) second.get(1);
                    int b2 = (int) second.get(2);
                    lastState.put("color2", setRGB(r2, g2, b2));
                }

                lastState.put("onOff", res.on);
                lastState.put("bright", res.bri);
                lastState.put("presets", res.ps);
                lastState.put("device_id", deviceId);
                lastState.put("color1", setRGB(r, g, b));

                return lastState;
                // Get the root element
            } catch (Exception e) {
                System.err.println(e);
            }

        }
        return lastState;
    }

    public void publishForInfo(String deviceId) {
        sendToTopic(deviceTopic, "{\"v\":true}");
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
            sendToTopic(deviceTopic, "T");
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> powerOnOff(boolean on) {
        try {
            sendToTopic(deviceTopic, String.valueOf(on));
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

    @Async
    public CompletableFuture<String> setBrightness(int brightness) {
        try {
            sendToTopic(deviceTopic, String.valueOf(brightness));
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }

    }

    @Async
    public CompletableFuture<String> setPresets(int presets) {
        try {
            String payload = String.format("{\"ps\": %d}", presets);
            sendToTopic(deviceTopic + "/api", "FX=" + presets);
            return CompletableFuture.completedFuture("success");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("error");
        }
    }

}
