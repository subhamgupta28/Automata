package dev.automata.automata.service;

import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AutomationHandler {

    private final DeviceRepository deviceRepository;
    private final DataRepository dataRepository;


    public void getCurrentValue(String key){

    }


}
