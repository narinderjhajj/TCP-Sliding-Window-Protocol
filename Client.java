import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Client {
    PrintWriter writer;
    int hostPort;//host Port number
    InetAddress destAddress;//destination IP addresss
    int destPort;//destination Port
    DatagramSocket socket;
    int my_isn;//host sequence number
    int base;// base of the window
    int windowSize = 1;//size of the window
    boolean unsuccessfulTransmission = false;
    Dgram lastSentPacket;

    //constructor
    Client(int port, InetAddress destIp, int destPort) throws IOException, ClassNotFoundException {
        hostPort = port;
        destAddress = destIp;
        this.destPort = destPort;
        try {
            socket = new DatagramSocket(hostPort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        //intiating handshake
        System.out.println("Connect to Server @: " + "172.20.10.4" + "\t Port: " + destPort);
        intiateSetup();
    }

    //Method for implementing intiateSetup
    public void intiateSetup() throws IOException, ClassNotFoundException {
        System.out.println("Request for connection");
        Dgram handShakePacket;
        handShakePacket = new Dgram(my_isn, true, false);
        Dgram.sendpckt(handShakePacket, socket, destAddress, destPort);
        my_isn++;
        lastSentPacket = handShakePacket;
        Dgram newPacket = Dgram.createSeg(socket);
        while (!newPacket.sync) {
            repeatPacket();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    //Method for sending the packet
    int sentCount = 0;
    int packetCount = 0;
    public void send() {
        //Start listen acknowledgment thread for this send packet
        ListenAcks hearAck = new ListenAcks();
        Thread ackThread = new Thread(hearAck);
        ackThread.start();
        //intializing all parameter for controlling flow of packets
        //and window size
        base = my_isn;
        System.out.println("after handshake: " + my_isn);
        windowSize = 1;
        int numOfSegments = 10000000;
        int segCount = 0;
        int nextSeq = base;
        Dgram newPacket = new Dgram();
        packetCount++;
        try {
            //checking if the whole byte array is exhausted
            while (segCount < numOfSegments && my_isn < Math.pow(2, 16)) {
                sentCount++;
                if(sentCount == 1000){
                    System.out.println("sent count " + sentCount);
                    sentCount=0;
                }
                //chekcing if some bytes are available in window for sending
                if (nextSeq - base < windowSize) {
                    //forming and sending packet
                    newPacket.setSeqNum(my_isn);
                    // newPacket.setAckNum(dest_isn);
                    System.out.println("Sent Seq Num:" + my_isn);
                    //sending packet
                    Dgram.sendpckt(newPacket, socket, destAddress, destPort);
                    nextSeq += Math.pow(2, 10);
                    // nextSeq++;
                    my_isn = nextSeq;
                    segCount++;
                    lastSentPacket = newPacket;
                    Thread.sleep(10);
                }
            }
            //sending finish packet
            newPacket.setSeqNum(my_isn);
            // newPacket.setAckNum(dest_isn);
            newPacket.setFin();
            System.out.println("Connection Closed!");
            //sending packet
            Dgram.sendpckt(newPacket, socket, destAddress, destPort);
            my_isn++;

        } catch (IOException | InterruptedException e) {//| InterruptedException e) {
            e.printStackTrace();
        }
        finally{
        }

    }

    public void repeatPacket() {
        try {
            DateFormat dateFormat = new SimpleDateFormat("SSS");
            Date date = new Date();
            System.out.println("Repeating Packet: " + lastSentPacket.seqNum + "time " + dateFormat.format(date));
            Dgram.sendpckt(lastSentPacket, socket, destAddress, destPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //Threadd for listening to the acks which are sent for received packets
    class ListenAcks implements Runnable {
        public boolean finished = false;

        public void run() {
            try{
                writer = new PrintWriter("windowSizes.txt", "UTF-8");

            }catch (Exception e){

            }
            Dgram newRcvdPacket = null;
            //parameter for controlling timeout of the ack
            int timeOutVal = 1000;
            long startTime = 0;
            long endTime = 0;
            int rtt;
            do {
                try {
                    DateFormat dateFormat = new SimpleDateFormat("SSS");
                    Date date = new Date();
                    writer.println(dateFormat.format(date) + " " + windowSize);
                    //time for the acks to receive
                    socket.setSoTimeout(timeOutVal);
                    startTime = System.currentTimeMillis();
                    System.out.println("Listening...Acks");
                    endTime = System.currentTimeMillis();
                    //forming and sending the ackpacket
                    newRcvdPacket = Dgram.createSeg(socket);
                    //if (newRcvdPacket.ack && newRcvdPacket.getSeqNum() == sentSeqNum + 1) {
                    if (newRcvdPacket.ack && newRcvdPacket.getAckNum() == my_isn) {
                        base = newRcvdPacket.getAckNum();
                        //base = newRcvdPacket.getSeqNum();
                        System.out.println("Received and required ackNum :  " + newRcvdPacket.getAckNum());
                        // my_isn += Math.pow(2, 10);
                        if (windowSize < Math.pow(2, 16)) {
                            if (unsuccessfulTransmission) {
                                windowSize++;
                                System.out.println("Window Size Increased by One to:" + windowSize);
                                continue;
                            }
                            windowSize *= 2;
                            System.out.println("Window Size doubled to:" + windowSize);
                        }
                    } else {
                        if (windowSize > 1)
                            windowSize /= 2;
                        System.out.println("Required ack : " + (my_isn) + " : Received ackNum :  " + newRcvdPacket.getAckNum());
                        System.out.println("invalid ack ");
                        repeatPacket();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    unsuccessfulTransmission = true;
                    if (windowSize > 1)
                        windowSize /= 2;
                    System.out.println("Window Size Decreased to:" + windowSize);
                    System.out.println("timeoutAck");
                    repeatPacket();
                }
                //calculating new estimated RTT
                rtt = (int) (endTime - startTime);
                timeOutVal = calculateNewTime(timeOutVal, rtt);
            } while (!newRcvdPacket.finish);
writer.close();
        }

        //calculating new estimated RTT
        int calculateNewTime(int timeOutVal, int rtt) {
            return (int) (timeOutVal + 0.125 * (rtt));
        }
    }

    //Main method
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //Getting reference of this class for calling non static fields
        Client clnt;
        //initialize socket InetAddress.getByName("192.168.1.101")
        System.out.println(InetAddress.getLocalHost());
        clnt = new Client(6564, InetAddress.getLocalHost(), 6565);//InetAddress.getLocalHost(), 6565);//InetAddress.getByName(args[0])
        System.out.println("handshake done!!!");
        clnt.send();
        System.out.println("All segments sent!");
    }
}
