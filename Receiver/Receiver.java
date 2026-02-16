import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class Receiver{

    public static void main(String[] args) {

        if(args.length < 5) {
            System.out.println("Error missing required arguments");
            return;
        }
    
        String sender_ip = args[0];
        int sender_ack_port = Integer.parseInt(args[1]);
        int rcv_data_port = Integer.parseInt(args[2]);
        String output_file = args[3];
        int rn = Integer.parseInt(args[4]);
    
        int expected_seq;

        try {
    
            DatagramSocket receiver_socket = new DatagramSocket(rcv_data_port);
    
            byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
            DatagramPacket received_datagram = new DatagramPacket(buffer, buffer.length);
            
            while(true){

                receiver_socket.receive(received_datagram);
                
                DSPacket packet = new DSPacket(received_datagram.getData());

                if(packet.getSeqNum()==0 && packet.getType()==DSPacket.TYPE_SOT){
                    System.out.println("SOT received from Sender");
                    DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                    byte[] raw_bytes = ACK_packet.toBytes();
        
                    DatagramPacket ACK_datagram = new DatagramPacket(
                        raw_bytes,
                        raw_bytes.length,
                        received_datagram.getAddress(),
                        sender_ack_port
                    );
        
                    System.out.println("Sending ACK packet");
                    expected_seq = 1;
                    // open file
                    receiver_socket.send(ACK_datagram);
        
                }

                else if (expected_seq == packet.getSeqNum()) {

                    if(packet.getType()==DSPacket.TYPE_EOT) {

                        // teardown

                        DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_EOT, expected_seq, null);
                        byte[] raw_bytes = ACK_packet.toBytes();
            
                        DatagramPacket ACK_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            received_datagram.getAddress(),
                            sender_ack_port
                        );

                        // close file
                        receiver_socket.send(ACK_datagram);
                        receiver_socket.close();
                        break;

                    }

                    else if(packet.getType()==DSPacket.TYPE_DATA) {

                        DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, expected_seq, null);
                        byte[] raw_bytes = ACK_packet.toBytes();
            
                        DatagramPacket ACK_datagram = new DatagramPacket(
                            raw_bytes,
                            raw_bytes.length,
                            received_datagram.getAddress(),
                            sender_ack_port
                        );

                        receiver_socket.send(ACK_datagram);
                        expected_seq ++;
                        // write data to file
                        

                    }

                }

                else {
                
                    // send ACK for last received seq num
                    DSPacket ACK_packet = new DSPacket(DSPacket.TYPE_ACK, expected_seq-1, null);
                    byte[] raw_bytes = ACK_packet.toBytes();
        
                    DatagramPacket ACK_datagram = new DatagramPacket(
                        raw_bytes,
                        raw_bytes.length,
                        received_datagram.getAddress(),
                        sender_ack_port
                    );
                    receiver_socket.send(ACK_datagram);

                }

            }
        
            
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    
    
    }
    

}


