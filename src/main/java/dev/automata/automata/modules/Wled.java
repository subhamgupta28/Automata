package dev.automata.automata.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


public class Wled {
    private final String ipAddress;
    private final RestTemplate restTemplate;

    public Wled(String ipAddress) {
        this.ipAddress = ipAddress + "/win";
        restTemplate = new RestTemplate();
    }

    public String powerOnOff(boolean on) {
        return restTemplate.getForObject(ipAddress + "&T=2", String.class);
    }

    public String setBrightness(int brightness) {
        return restTemplate.getForObject(ipAddress + "&A=" + brightness, String.class);
    }

    public String  setPresets(int presets) {
       return restTemplate.getForObject(ipAddress + "&PL=" + presets, String.class);
    }

    public String setRGB(int red, int green, int blue) {
        return restTemplate.getForObject(ipAddress + "&R=" + red + "&G=" + green + "&B=" + blue, String.class);
    }

    public String setEffect(int effect) {
        return restTemplate.getForObject(ipAddress + "&FX=" + effect, String.class);
    }
}
