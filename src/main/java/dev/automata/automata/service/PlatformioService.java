package dev.automata.automata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

@Service
@RequiredArgsConstructor
public class PlatformioService {

    private final String PIO_CMD = "/home/subham/.platformio/penv/bin/platformio";

    public String runCommand(String[] args, String workingDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(args);
        builder.directory(new File(workingDir));
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    public String buildProject(String projectDir) throws IOException {
        return runCommand(new String[]{PIO_CMD, "run"}, projectDir);
    }

    public String uploadProject(String projectDir) throws IOException {
        return runCommand(new String[]{PIO_CMD, "run", "-t", "upload"}, projectDir);
    }
}

