import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

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
    
        // Stop and weight
        if(args.length == 5) {

            try {
                
                sender_socket = new DatagramSocket(sender_ack_port);
                sender_socket.setSoTimeout(timeout_ms);

                DSPacket SOT_packet = new DSPacket(DSPacket.TYPE_SOT, 0, null);
                byte[] raw_bytes = SOT_packet.toBytes();
    
                DatagramPacket UDP_datagram = new DatagramPacket(
                    raw_bytes,
                    raw_bytes.length,
                    InetAddress.getByName(rcv_ip),
                    rcv_data_port
                );

                int timeout_count = 0;

                // initial handshake
                while (timeout_count < 3){

                    System.out.println("Sending SOT packet");
                    sender_socket.send(UDP_datagram);

                    try {
        
                        byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                        DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
            
                        sender_socket.receive(ackUDP);
                        System.out.println("Received ACK packet");
        
            
                        DSPacket ackPacket = new DSPacket(ackUDP.getData());
            
                        if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == 0) {
            
                            System.out.println("Handshake complete!");
                            break;
                        }
            
            
            
                    } catch (SocketTimeoutException  e) {
                        // TODO: handle exception
                        timeout_count ++;
                        System.out.println("Timeout waiting for ACK (" + timeout_count + "/3)");
                    }

                }

                if(timeout_count==3){
                    System.out.println("Error, failed to complete handshake");
                    return;
                }

                // open file
                int sequence_number = 1;
                timeout_count=0;

                byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
                sender_socket.setSoTimeout(timeout_ms);

                input_stream = new FileInputStream(input_file);
                byte[] file_buffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];


                while(true) {
                    int bytes_read = input_stream.read(file_buffer);

                    if(bytes_read==-1){
                        break;
                    }

                    timeout_count = 0;

                    byte[] payload = Arrays.copyOf(file_buffer, bytes_read);

                    DSPacket DATA_packet = new DSPacket(DSPacket.TYPE_DATA, sequence_number, payload);
                    raw_bytes = DATA_packet.toBytes();
        
                    UDP_datagram = new DatagramPacket(
                        raw_bytes,
                        raw_bytes.length,
                        InetAddress.getByName(rcv_ip),
                        rcv_data_port
                    );

                    

                    // maybe loop through file 124 bytes per time
                    while(timeout_count < 3){
                        sender_socket.send(UDP_datagram);

                        try {
                        
                            // read max 124 bytes from file, put this in data and replace for null
                        
                            

                            sender_socket.receive(ackUDP);
                            DSPacket ackPacket = new DSPacket(ackUDP.getData());
                
                            if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == sequence_number) {
                
                                System.out.println("ACK Received!");
                                sequence_number ++;
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

                // send EOF when the file is done and then break from loops

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
                        
                            sender_socket.receive(ackUDP);
                            DSPacket ackPacket = new DSPacket(ackUDP.getData());
                
                            if(ackPacket.getType() == DSPacket.TYPE_EOT && ackPacket.getSeqNum() == sequence_number) {
        
                                System.out.println("EOT Received!");
                                break;
                            }
    
                        } catch (SocketTimeoutException  e) {
                            // TODO: handle exception
                            timeout_count ++;
                            System.out.println("Timeout waiting for EOT ACK (" + timeout_count + "/3)");
                        }

                }


                // print the total time

            } catch (Exception e) {
                // TODO: handle exception
                System.out.println(e.getStackTrace());
            } finally {

                if(input_stream != null) input_stream.close();
                if(sender_socket != null) sender_socket.close();
            }

        }
    
    
    
        // go back N
        else if(args.length == 6){
            int window_size = Integer.parseInt(args[5]);
        }
    

    }
    
}

