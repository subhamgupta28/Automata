package dev.automata.automata.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;


public class Wled {
    private final String ipAddress;
    private final RestTemplate restTemplate;

    public Wled(String ipAddress) {
        this.ipAddress = ipAddress + "/win";
        restTemplate = new RestTemplate();
    }

    public String powerOnOff(boolean on) {
        var res = restTemplate.getForObject(ipAddress + "&T=2", String.class);
        if (res != null) {
            var bht = res.split(">")[3].replace("</ac", "");
            return Integer.parseInt(bht) > 0 ? "on" : "off";
        } else
            return "off";
    }

    public String setBrightness(int brightness) {
        var res = restTemplate.getForObject(ipAddress + "&A=" + brightness, String.class);
        if (res != null) {
            var bht = res.split(">")[3].replace("</ac", "");
            return Integer.parseInt(bht) == brightness ? "success" : "error";
        } else
            return "error";
    }

    public String setPresets(int presets) {
        var res = restTemplate.getForObject(ipAddress + "&PL=" + presets, String.class);
        if (res != null) {
            var bht = res.split(">")[41].replace("</ps", "");
            return Integer.parseInt(bht) == presets ? "success" : "error";
        } else
            return "error";

    }

    public String setRGB(int red, int green, int blue) {
        return restTemplate.getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
    }

    public String setEffect(int effect) {
        return restTemplate.getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
