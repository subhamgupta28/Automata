package dev.automata.automata.firmware_builder;

import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FirmwareBuilderService {

    private final FirmwareService firmwareService;
    private final MainService mainService;

    //    @Scheduled(fixedRate = 120000)
    public void run() {
//        var devices = mainService.getAllDevice();

//        firmwareService.registerDevice();
    }
}
