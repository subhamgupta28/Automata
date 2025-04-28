package dev.automata.automata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/*
PlatformIO Core has been successfully installed into an isolated environment `/home/subham/.platformio/penv`!

The full path to `platformio.exe` is `/home/subham/.platformio/penv/bin/platformio`

If you need an access to `platformio.exe` from other applications, please install Shell Commands
(add PlatformIO Core binary directory `/home/subham/.platformio/penv/bin` to the system environment PATH variable):

See https://docs.platformio.org/page/installation.html#install-shell-commands
* */

@RestController
@RequestMapping("/api/v1/platformio")
@RequiredArgsConstructor
public class PlatformIOController {

    private final PlatformioService pioService;

    @PostMapping("/build")
    public ResponseEntity<String> build(@RequestParam String projectDir) {
        try {
            String result = pioService.buildProject(projectDir);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam String projectDir) {
        try {
            String result = pioService.uploadProject(projectDir);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}

