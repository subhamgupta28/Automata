package dev.automata.automata.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Service
public class UdpBroadcastService {

    private static final String BROADCAST_IP = "192.168.1.255"; // Broadcast to all devices
    private static final int UDP_PORT = 12345;
    private static final String MESSAGE = "Hello from Raspberry Pi";

    @Scheduled(fixedRate = 30000) // Broadcast every 5 seconds
    public void broadcastUdpMessage() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] buffer = MESSAGE.getBytes();

            InetAddress address = InetAddress.getByName(BROADCAST_IP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, UDP_PORT);

            socket.send(packet);
            System.out.println("Broadcasted message: " + MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

