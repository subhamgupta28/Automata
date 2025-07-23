package dev.automata.automata.modules;

import org.springframework.stereotype.Controller;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioReactiveWled {

    private final String multicastIP = "239.0.0.1";
    private final int udpPort = 21324;

    public void send(AudioData data) {
        try {
            byte[] packet = buildPacket(data);
            InetAddress group = InetAddress.getByName(multicastIP);
            DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, group, udpPort);
            DatagramSocket socket = new DatagramSocket();
            socket.send(datagramPacket);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] buildPacket(AudioData data) {
        byte[] packet = new byte[44]; // V2 format

        // Header "00002"
        packet[0] = '0'; packet[1] = '0'; packet[2] = '0'; packet[3] = '0'; packet[4] = '2'; packet[5] = 0;

        // Filler (2 bytes)
        packet[6] = 0;
        packet[7] = 0;

        // sampleRaw (float)
        int sampleRawBits = Float.floatToIntBits(data.volume);
        packet[8]  = (byte) ((sampleRawBits >> 24) & 0xFF);
        packet[9]  = (byte) ((sampleRawBits >> 16) & 0xFF);
        packet[10] = (byte) ((sampleRawBits >> 8) & 0xFF);
        packet[11] = (byte) (sampleRawBits & 0xFF);

        // sampleSmth (copy sampleRaw)
        packet[12] = packet[8];
        packet[13] = packet[9];
        packet[14] = packet[10];
        packet[15] = packet[11];

        // samplePeak
        packet[16] = (byte) (data.peak ? 1 : 0);
        packet[17] = 0; // reserved

        // fftResult[16]
        for (int i = 0; i < 16; i++) {
            packet[18 + i] = (byte) (i < data.fft.length ? data.fft[i] : 0);
        }

        // FFT_Magnitude (float)
        int magBits = Float.floatToIntBits((float) data.magnitude);
        packet[36] = (byte) ((magBits >> 24) & 0xFF);
        packet[37] = (byte) ((magBits >> 16) & 0xFF);
        packet[38] = (byte) ((magBits >> 8) & 0xFF);
        packet[39] = (byte) (magBits & 0xFF);

        // FFT_MajorPeak (float)
        int freqBits = Float.floatToIntBits((float) data.frequency);
        packet[40] = (byte) ((freqBits >> 24) & 0xFF);
        packet[41] = (byte) ((freqBits >> 16) & 0xFF);
        packet[42] = (byte) ((freqBits >> 8) & 0xFF);
        packet[43] = (byte) (freqBits & 0xFF);

        return packet;
    }

}
