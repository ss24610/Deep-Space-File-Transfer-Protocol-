import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.io.*;

public class Sender {

    public static void main(String[] args) {

        if(args.length != 5 && args.length != 6 ){
            System.out.println("Error missing required arguments");
            System.out.println("Sender must be initialized as either:");
            System.out.println("Stop and Wait: java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms>");
            System.out.println("Go Back N: java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]");
            return;
        }
    
        String rcv_ip = args[0];
        int rcv_data_port;
        int sender_ack_port;
        int timeout_ms;

        try {

            rcv_data_port = Integer.parseInt(args[1]);
            sender_ack_port = Integer.parseInt(args[2]);
            timeout_ms = Integer.parseInt(args[4]);
            
        } catch (NumberFormatException e) {
            // TODO: handle exception
            System.err.println("ERROR receiver/sender port number and timeout interval must be supplied as integers.");
            return;
        }
        
        String input_file = args[3];

        FileInputStream input_stream = null;
        DatagramSocket sender_socket = null;

        DatagramPacket UDP_datagram = null;
        // Initial Handshake
        int timeout_count = 0;
        long start_time = -1;
        long stop_time = -1;

        try {

            // Send packet with seq 0 and type SOT
            sender_socket = new DatagramSocket(sender_ack_port);
            sender_socket.setSoTimeout(timeout_ms);
            DSPacket SOT_packet = new DSPacket(DSPacket.TYPE_SOT, 0, null);
            byte[] raw_bytes = SOT_packet.toBytes();

            UDP_datagram = new DatagramPacket(
                raw_bytes,
                raw_bytes.length,
                InetAddress.getByName(rcv_ip),
                rcv_data_port
            );

            // Continue transmitting until the handshake is completed
            // 3 attempts, if failure then trigger critical error and shutdown
            while (timeout_count < 3){

                System.out.println("Sending SOT packet");
                sender_socket.send(UDP_datagram);

                try {
    
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
        
                    sender_socket.receive(ackUDP);
                    System.out.println("Received packet");
        
                    DSPacket ackPacket = new DSPacket(ackUDP.getData());
        
                    // Sender received ACK for handshake, start transfer timer
                    if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == 0) {
                        
                        // start transfer timer
                        start_time = System.nanoTime();
                        System.out.println("Received ACK, Handshake complete!");
                        
                        break;

                    }
        
                } catch (SocketTimeoutException  e) {
                    // TODO: handle exception
                    timeout_count ++;
                    System.out.println("Timeout waiting for handshake ACK (" + timeout_count + "/3)");
                }

            }

            if(timeout_count==3){
                System.out.println("Error, failed to complete handshake");
                return;
            }

            // open file
            input_stream = new FileInputStream(input_file);
            byte[] file_buffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
            byte[] payload;
            int bytes_read = 0;

            timeout_count = 0;

            // Stop and wait
            if(args.length == 5) {

                int sequence_number = 1;
                
                while(true) {
                    bytes_read = input_stream.read(file_buffer);

                    if(bytes_read==-1){
                        break;
                    }

                    timeout_count = 0;

                    payload = Arrays.copyOf(file_buffer, bytes_read);

                    DSPacket DATA_packet = new DSPacket(DSPacket.TYPE_DATA, sequence_number, payload);
                    raw_bytes = DATA_packet.toBytes();
        
                    UDP_datagram = new DatagramPacket(
                        raw_bytes,
                        raw_bytes.length,
                        InetAddress.getByName(rcv_ip),
                        rcv_data_port
                    );

                    while(timeout_count < 3){
                        sender_socket.send(UDP_datagram);

                        try {
                            
                            byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                            DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);

                            sender_socket.receive(RESPONSE_datagram);
                            DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                
                            if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == (sequence_number%128)) {
                
                                System.out.println("ACK Received!");
                                sequence_number = (sequence_number + 1) % 128;
                                break;
                            }
    
                        } catch (SocketTimeoutException  e) {
                            // TODO: handle exception
                            timeout_count ++;
                            System.out.println("Timeout waiting for ACK (" + timeout_count + "/3)");
                        }

                    }

                    if (timeout_count == 3) {
                        System.out.println("Transfer failed.");
                        break;
                    }
                    
                }

                // send EOF when the file is done or timeout failure and then break from loop
                
                DSPacket EOT_packet = new DSPacket(DSPacket.TYPE_EOT, sequence_number, null);
                raw_bytes = EOT_packet.toBytes();
                timeout_count = 0;

                UDP_datagram = new DatagramPacket(
                    raw_bytes,
                    raw_bytes.length,
                    InetAddress.getByName(rcv_ip),
                    rcv_data_port
                );

                while(timeout_count < 3){
                    sender_socket.send(UDP_datagram);

                    try {
                        byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                        DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);
                        sender_socket.receive(RESPONSE_datagram);
                        DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                
                        if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == (sequence_number%128)) {
                            stop_time = System.nanoTime();
                            System.out.println("EOT Received!");
                            break;
                        }

                    } catch (SocketTimeoutException  e) {
                        // TODO: handle exception
                        timeout_count ++;
                        System.out.println("Timeout waiting for EOT ACK (" + timeout_count + "/3)");
                    }

                }

                if(input_stream != null) input_stream.close();

            }

            // go back N
            else if(args.length==6){

                int window_size = Integer.parseInt(args[5]);
                int base = 1;
                int nextSeq = 1;
                HashMap<Integer, DSPacket> window_buffer = new HashMap<>();
                List<DSPacket> chaos_group = new ArrayList<>();

                // continue unil EOF or while we still have unack packets in window
                while(bytes_read!=-1 || window_buffer.size() != 0){

                    // Send window of packets 
                    while(((nextSeq - base + 128) % 128) < window_size){
                        bytes_read = input_stream.read(file_buffer);

                        if(bytes_read==-1){
                            break;
                        }

                        payload = Arrays.copyOf(file_buffer, bytes_read);

                        DSPacket DATA_packet = new DSPacket(DSPacket.TYPE_DATA, nextSeq, payload);
                        window_buffer.put(nextSeq,DATA_packet);
                        nextSeq = (nextSeq + 1) % 128;

                        // for chaos engine, create a list of size 4 and append packets to it
                        // when size is 4, apply the chaos engine aand then iterate over the packets
                        // send each packet, then clear list
                        // only send packets in groups of 4?
                        chaos_group.add(DATA_packet);

                        if(chaos_group.size()==4){
                            chaos_group = ChaosEngine.permutePackets(chaos_group);

                            for(DSPacket packet: chaos_group){

                                raw_bytes = packet.toBytes();
            
                                UDP_datagram = new DatagramPacket(
                                    raw_bytes,
                                    raw_bytes.length,
                                    InetAddress.getByName(rcv_ip),
                                    rcv_data_port
                                );
                                
                                sender_socket.send(UDP_datagram);

                            }

                            chaos_group.clear();

                        }
                        
                    }

                    // Send if not a group of 4
                    for(DSPacket packet: chaos_group){

                        raw_bytes = packet.toBytes();
    
                        UDP_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            InetAddress.getByName(rcv_ip),
                            rcv_data_port
                        );

                        sender_socket.send(UDP_datagram);

                    }

                    chaos_group.clear();

                    // receive ACKs
                    try {
                        byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                        DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);
                        
                        sender_socket.receive(RESPONSE_datagram);

                        DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                        int ack_num = ackPacket.getSeqNum();

                        // the received ACK has sequence number in order
                        if(ackPacket.getType() == DSPacket.TYPE_ACK && ((ack_num - base + 128) % 128 < (nextSeq - base + 128) % 128)) {
                            // reset the timeout counts
                            timeout_count = 0;

                            System.out.println("ACK Received for sequence number: " + ackPacket.getSeqNum());

                            int old_base = base;
                            base = (ack_num+1)%128;

                            // go over the windowbuffer and remove the packets that were cumulative ACKed
                            while(old_base!= base){
                                window_buffer.remove(old_base);
                                old_base = (old_base+1)%128;
                            }

                        }

                    } 
                    
                    catch (SocketTimeoutException e) {
                        // TODO: handle exception
                        // if base is still in window, increment timeout count.
                        // if timeout count is 3, then we have reached failure so exit loop
                        // otherwise retransmit window
                        // retransmit the window
                        timeout_count++;
                        if(timeout_count==3) break;

                        // retransmit window
                        for(int i = base; i != nextSeq; i=(i+1)%128){
                            DSPacket DATA_packet = window_buffer.get(i);

                            raw_bytes = DATA_packet.toBytes();

                            chaos_group.add(DATA_packet);

                            if(chaos_group.size()==4){
                                chaos_group = ChaosEngine.permutePackets(chaos_group);

                                for(DSPacket packet: chaos_group){

                                    raw_bytes = packet.toBytes();
                
                                    UDP_datagram = new DatagramPacket(
                                        raw_bytes,
                                        raw_bytes.length,
                                        InetAddress.getByName(rcv_ip),
                                        rcv_data_port
                                    );

                                    sender_socket.send(UDP_datagram);

                                }

                                chaos_group.clear();

                            }
                            
                        }

                        // repeat the same logic for chaos engine above
                        // Send if not a group of 4
                        for(DSPacket packet: chaos_group){

                            raw_bytes = packet.toBytes();
        
                            UDP_datagram = new DatagramPacket(
                                raw_bytes,
                                raw_bytes.length,
                                InetAddress.getByName(rcv_ip),
                                rcv_data_port
                            );

                            sender_socket.send(UDP_datagram);

                        }

                        chaos_group.clear();

                    }
                    
                }

                // EOF or critical error, send EOT 

                DSPacket EOT_packet = new DSPacket(DSPacket.TYPE_EOT, nextSeq, null);
                raw_bytes = EOT_packet.toBytes();
                timeout_count = 0;

                UDP_datagram = new DatagramPacket(
                    raw_bytes,
                    raw_bytes.length,
                    InetAddress.getByName(rcv_ip),
                    rcv_data_port
                );

                while(timeout_count < 3){
                    sender_socket.send(UDP_datagram);

                    try {
                        byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                        DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);
                        sender_socket.receive(RESPONSE_datagram);
                        DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                
                        if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == (nextSeq%128)) {
                            stop_time = System.nanoTime();
                            System.out.println("EOT Received!");
                            break;
                        }

                    } catch (SocketTimeoutException  e) {
                        // TODO: handle exception
                        timeout_count ++;
                        System.out.println("Timeout waiting for EOT ACK (" + timeout_count + "/3)");
                    }

                }

                if(input_stream != null) input_stream.close();

            }

        }

        catch(IOException e) {
            System.out.println("Failure closing input file");
            System.out.println(e.getStackTrace());
        }
        
        catch (Exception e) {
            // TODO: handle exception
            System.out.println(e.getStackTrace());
        } 
        
        finally {

            // print the total time
            if(sender_socket != null) sender_socket.close();
            
            if(stop_time!=-1){
                long total_time = stop_time-start_time;
                System.out.println("Start Time: " + start_time + " Stop Time: " + stop_time + " Total Time: " + total_time);
            }
            
        }
    
    } 
        
}

