package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WledResponse {
    public boolean on;
    public int bri;
    public int transition;
    public int ps;
    public int pl;
    public int ledmap;
    public int lor;
    public int mainseg;
}
