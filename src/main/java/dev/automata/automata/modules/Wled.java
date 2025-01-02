package dev.automata.automata.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class Wled {
    private final String ipAddress;
    private final RestTemplate restTemplate;

    public Wled(String ipAddress) {
        this.ipAddress = ipAddress + "/win";
        restTemplate = new RestTemplate();
    }

    public Map<String, Object> getInfo(String deviceId) {
        var res = restTemplate.getForObject(ipAddress, String.class);
        if (res != null) {
            try {
                // Convert the XML string into a byte stream
                ByteArrayInputStream input = new ByteArrayInputStream(res.getBytes());

                // Initialize the XML Document Builder
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(input);

                // Normalize the document structure
                doc.getDocumentElement().normalize();

                boolean onOff = getTagValue(doc, "ac") > 0;
                int bright = getTagValue(doc, "ac");
                int preset = getTagValue(doc, "ps");

                var map =  new HashMap<String, Object>();
                map.put("onOff", onOff);
                map.put("bright", bright);
                map.put("preset", preset);
                map.put("device_id", deviceId);
                return map;
                // Get the root element
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return null;
    }
    private int getTagValue(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return Integer.parseInt(nodeList.item(0).getTextContent()); // Return the text content of the first matching element
        }
        return -1; // Return null if the tag is not found
    }
    public String powerOnOff(boolean on) {
        var res = restTemplate.getForObject(ipAddress + "&T=2", String.class);
        if (res != null) {
            var bht = res.split(">")[3].replace("</ac", "");
            return Integer.parseInt(bht) > 0 ? "Success" : "Error";
        } else
            return "Off";
    }

    public String setBrightness(int brightness) {
        var res = restTemplate.getForObject(ipAddress + "&A=" + brightness, String.class);
        if (res != null) {
            var bht = res.split(">")[3].replace("</ac", "");
            return Integer.parseInt(bht) == brightness ? "Success" : "Error";
        } else
            return "Error";
    }

    public String setPresets(int presets) {
        var res = restTemplate.getForObject(ipAddress + "&PL=" + presets, String.class);
        if (res != null) {
            var bht = res.split(">")[41].replace("</ps", "");
            return Integer.parseInt(bht) == presets ? "Success" : "Error";
        } else
            return "Error";

    }

    public String setRGB(int red, int green, int blue) {
        return restTemplate.getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
    }

    public String setEffect(int effect) {
        return restTemplate.getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
