package dev.automata.automata.modules;

import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.model.DeviceActionState;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Wled {
    private final String ipAddress;
    private final RestTemplate restTemplate;
    private static final HashMap<String, Object> lastState = new HashMap<>();


    public Wled(String ipAddress) {
        this.ipAddress = ipAddress + "/json/state";
        restTemplate = new RestTemplate();
    }

    public Map<String, Object> getInfo(String deviceId, DeviceActionState deviceState) {
        var res = restTemplate.getForObject(ipAddress, WledResponse.class);
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

    public String toggleOnOff() {
        try {
            var r = """
                    {"on": "t"}
                    """;

            var response = restTemplate.postForObject(ipAddress, r, Object.class);
            System.err.println(response);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    public String powerOnOff(boolean on) {
        lastState.put("onOff", on);

        try {
            var r = """
                    {"on": v}
                    """;

            var response = restTemplate.postForObject(ipAddress, r.replace("v", String.valueOf(on)), Object.class);
            System.err.println(response);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    public String setBrightness(int brightness) {
        lastState.put("bright", brightness);
        try {
            var r = """
                    {"bri": v}
                    """;

            var response = restTemplate.postForObject(ipAddress, r.replace("v", String.valueOf(brightness)), Object.class);
            System.err.println(response);
            return "success";
        } catch (Exception e) {
            return "error";
        }

    }

    public String setPresets(int presets) {
        lastState.put("presets", presets);
        try {
            var r = """
                    {"ps": v}
                    """;

            var response = restTemplate.postForObject(ipAddress, r.replace("v", String.valueOf(presets)), Object.class);
            System.err.println(response);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    public String setRGB(int red, int green, int blue) {
        return restTemplate.getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
    }

    public String setEffect(int effect) {
        return restTemplate.getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
