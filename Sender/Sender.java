import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

public class Sender {

    public static void main(String[] args) {

        if(args.length < 5){
            System.out.println("Error missing required arguments");
            return;
        }
    
        String rcv_ip = args[0];
        int rcv_data_port = Integer.parseInt(args[1]);
        int sender_ack_port = Integer.parseInt(args[2]);
        String input_file = args[3];
        int timeout_ms = Integer.parseInt(args[4]);

        FileInputStream input_stream = null;
        DatagramSocket sender_socket = null;

        DatagramPacket UDP_datagram = null;
        // Initial Handshake
        int timeout_count = 0;

        try {

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

            

            while (timeout_count < 3){

                System.out.println("Sending SOT packet");
                sender_socket.send(UDP_datagram);

                try {
    
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
        
                    sender_socket.receive(ackUDP);
                    System.out.println("Received packet");
    
        
                    DSPacket ackPacket = new DSPacket(ackUDP.getData());
        
                    if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == 0) {
        
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

            // set timer to track length

            // Stop and wait
            if(args.length == 5) {

        
                int sequence_number = 1;

                byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);

                
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
                        
                            sender_socket.receive(RESPONSE_datagram);
                            DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                
                            if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == (sequence_number%128)) {
                
                                System.out.println("ACK Received!");
                                sequence_number ++;
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
                        input_stream.close();
                        break;
                    }
                    
                }


                // send EOF when the file is done and then break from loop
                
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
                        
                        sender_socket.receive(RESPONSE_datagram);
                        DSPacket ackPacket = new DSPacket(RESPONSE_datagram.getData());
                
                        if(ackPacket.getType() == DSPacket.TYPE_EOT && ackPacket.getSeqNum() == (sequence_number%128)) {
        
                            System.out.println("EOT Received!");
                            break;
                        }

                    } catch (SocketTimeoutException  e) {
                        // TODO: handle exception
                        timeout_count ++;
                        System.out.println("Timeout waiting for EOT ACK (" + timeout_count + "/3)");
                    }

                }

            }

            // go back N
            else if(args.length==6){

                int window_size = Integer.parseInt(args[5]);
                int base = 1;
                int nextSeq = 1;
                HashMap<Integer, DatagramPacket> window_buffer = new HashMap<>();

                byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket RESPONSE_datagram = new DatagramPacket(buffer, buffer.length);

                // continue unil EOF or while we still have unack packets in window
                while(bytes_read!=-1 || window_buffer.size() != 0){

                    // Send window of packets 
                    while((((nextSeq - base + 128) % 128) < window_size) && bytes_read!=-1){
                        bytes_read = input_stream.read(file_buffer);

                        if(bytes_read==-1){
                            break;
                        }

                        payload = Arrays.copyOf(file_buffer, bytes_read);

                        DSPacket DATA_packet = new DSPacket(DSPacket.TYPE_DATA, nextSeq, payload);
                        raw_bytes = DATA_packet.toBytes();
            
                        UDP_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            InetAddress.getByName(rcv_ip),
                            rcv_data_port
                        );

                        // store packet in buffer
                        window_buffer.put(nextSeq,UDP_datagram);
                        nextSeq = (nextSeq + 1) % 128;
                        sender_socket.send(UDP_datagram);

                        // for chaos engine, create a list of size 4 and append packets to it
                        // when size is 4, apply the chaos engine aand then iterate over the packets
                        // send each packet, then clear list
                        // only send packets in groups of 4?
                    

                    }
                    // if that list isnt empty, send remaining packets and then clear it

                    // receive ACKs
                    try {
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
                        if(window_buffer.get(base)!=null) timeout_count++;
                        if(timeout_count==3) break;

                        for(int i = base; i != nextSeq; i=(i+1)%128){
                            DatagramPacket datagram = window_buffer.get(i);
                            sender_socket.send(datagram);
                        }

                        // repeat the same logic for chaos engine above
                        

                    }
                    
                }

            }




        }




        catch (Exception e) {
            // TODO: handle exception
            System.out.println(e.getStackTrace());
        } 
        
        
        finally {

            // print the total time

            if(input_stream != null) input_stream.close();
            if(sender_socket != null) sender_socket.close();
        }
    


            
    } 
        
         
}

