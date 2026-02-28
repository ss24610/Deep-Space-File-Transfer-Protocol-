import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class Receiver{

    public static void main(String[] args) {

        if(args.length != 5 && args.length != 6 ){
            System.out.println("Error missing required arguments");
            System.out.println("Receiver must be initialized as either:");
            System.out.println("Stop and Wait: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>");
            System.out.println("Go Back N: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN> [window_size]");
            return;
        }
    
        String sender_ip = args[0];
        int sender_ack_port;
        int rcv_data_port;
        int rn;

        int total_acks = 0;

        try {
            sender_ack_port = Integer.parseInt(args[1]);
            rcv_data_port = Integer.parseInt(args[2]);
            rn = Integer.parseInt(args[4]);
            
        } catch (NumberFormatException e) {
            // TODO: handle exception
            System.err.println("ERROR receiver/sender port number and reliability number (RN) must be supplied as integers.");
            return;
        }

        String output_file = args[3];
        
        int expected_seq = 0;
        DatagramSocket receiver_socket = null;

        try {
    
            receiver_socket = new DatagramSocket(rcv_data_port);
            FileOutputStream output_stream = null;
            
            // Stop and Wait receiver
            if(args.length == 5){
                
                while(true){

                    total_acks++;

                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket received_datagram = new DatagramPacket(buffer, buffer.length);
            
                    receiver_socket.receive(received_datagram);
                    
                    DSPacket packet = new DSPacket(received_datagram.getData());
    
                    // Received SOT datagram
                    if(packet.getSeqNum()==0 && packet.getType()==DSPacket.TYPE_SOT){
                        System.out.println("SOT received from Sender");
                        DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                        byte[] raw_bytes = ACK_packet.toBytes();
            
                        DatagramPacket ACK_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            InetAddress.getByName(sender_ip),
                            sender_ack_port
                        );
            
                        System.out.println("Sending ACK packet");
                        
                        // Set expected seq num to 1 to begin data transfer process
                        expected_seq = 1;
                        // open file
                        output_stream = new FileOutputStream(output_file);

                        if(!ChaosEngine.shouldDrop(total_acks, rn)) receiver_socket.send(ACK_datagram);
            
                    }
    
                    else if (expected_seq == packet.getSeqNum()) {
    
                        if(packet.getType()==DSPacket.TYPE_EOT) {
    
                            // teardown
                            DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, expected_seq, null);
                            byte[] raw_bytes = ACK_packet.toBytes();
                
                            DatagramPacket ACK_datagram = new DatagramPacket(
                                raw_bytes,
                                raw_bytes.length,
                                InetAddress.getByName(sender_ip),
                                sender_ack_port
                            );
    
                            // close file
                            output_stream.close();

                            // Continue listening until the sender receives an EOT ACK
                            if(!ChaosEngine.shouldDrop(total_acks, rn)) {
                                receiver_socket.send(ACK_datagram);
                                break;

                            }
                                
    
                        }
    
                        else if(packet.getType()==DSPacket.TYPE_DATA) {
    
                            DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, expected_seq, null);
                            byte[] raw_bytes = ACK_packet.toBytes();
                
                            DatagramPacket ACK_datagram = new DatagramPacket(
                                raw_bytes,
                                raw_bytes.length,
                                InetAddress.getByName(sender_ip),
                                sender_ack_port
                            );

                            expected_seq = (expected_seq+1)%128;
                            // write data to file
                            byte[] bytes = packet.getPayload();
                            output_stream.write(bytes);

                            if(!ChaosEngine.shouldDrop(total_acks, rn)) receiver_socket.send(ACK_datagram);
                            
                        }
    
                    }
    
                    // Received duplicate/retransmitted packet
                    else {
                    
                        // send ACK for last received seq num
                        int prev_seq = (expected_seq-1+128)%128;
                        DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, prev_seq, null);
                        byte[] raw_bytes = ACK_packet.toBytes();
            
                        DatagramPacket ACK_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            InetAddress.getByName(sender_ip),
                            sender_ack_port
                        );
                        if(!ChaosEngine.shouldDrop(total_acks, rn)) receiver_socket.send(ACK_datagram);
    
                    }
    
                }
            }

            // Go back N receiver
            // wania this is your section
            else if(args.length==6){

            }
            
         
        } 

        catch(IOException e) {

        }
        
        catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }

        finally{
            if(receiver_socket!=null) receiver_socket.close();

        }
    
    
    }
    

}


