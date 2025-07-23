package dev.automata.automata.modules;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class AudioData {
    public int volume;
    public boolean peak;
    public int[] fft; // length 16
    public double magnitude;
    public double frequency;
}

