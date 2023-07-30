import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class Server {
    PrintWriter writer;
    int serverPort;//server port number
    DatagramSocket serverSocket;//datagram socket
    InetAddress clientIp;//IP address of the client
    int clientPort;//Port of the client
    int client_isn;//Client sequence number
    int servernum;//Server sequence number
    LinkedList<Dgram> recvBuffer = new LinkedList<Dgram>();//recvBuffer as queue
    boolean endConnection = false;
    boolean clientConnected = false;
    int receivedSeqNum;
    Dgram lastSentAck;

    //constructor
    public Server(int port) {
        serverPort = port;
        try {
            serverSocket = new DatagramSocket(serverPort);
            writer = new PrintWriter("receivedSeqNums.txt", "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Methord for initiating handshake and receiving back socket to client
    public void accept() {
        listen();//listening for message from the client
    }

    //Method for listening to the data packets
    public void listen() {
        Dgram newRcvdPacket;
        try {
            do {
                System.out.println("Listening...");
                newRcvdPacket = Dgram.createSeg(serverSocket);
                if (newRcvdPacket.sync && !clientConnected) {
                    System.out.println("A client is trying to connect...");
                    clientPort = newRcvdPacket.prec;
                    clientIp = newRcvdPacket.rec;
                    System.out.println("Client Ip:" + clientIp + " \tand\tPort:" + clientPort);
                    receivedSeqNum = newRcvdPacket.getSeqNum();
                    System.out.println("Setup done ");
                    System.out.println("server seq number" + servernum + "\tand\t receivedSeqNum = " + receivedSeqNum);
                    servernum = 0;
                    receivedSeqNum++;
                    System.out.println("Replying Client with ack: " + receivedSeqNum);
                    Dgram handShakePacket = new Dgram(true, true, receivedSeqNum);
                    Dgram.sendpckt(handShakePacket, serverSocket, clientIp, clientPort);
                    servernum++;
                    clientConnected = true;
                    System.out.println("Connection Established Successfully!");
                    continue;
                } else {
                    if (newRcvdPacket.sync) {
                        System.out.println("Client already connected");
                        continue;
                    }
                    recvBuffer.add(newRcvdPacket);
                    //receivedSeqNum += Math.pow(2, 10);
                    //sending ack for this packet
                    receivedSeqNum = newRcvdPacket.seqNum;
                    System.out.println("Send ack for this packet!");
                    if (newRcvdPacket.finish) {
                        SendBack(true);
                    } else {
                        SendBack(false);
                    }
                }
                // System.out.println("Connection Finish = " + newRcvdPacket.fin);
            } while (!newRcvdPacket.finish);
        } catch (IOException | ClassNotFoundException e) {
            repeatAck();
            listen();
            System.out.println("Listen Timeout");
        }
        finally {
            writer.close();
        }
    }

    public void repeatAck() {
        try {
            System.out.println("Repeating Ack: " + lastSentAck.ackNum);
            Dgram.sendpckt(lastSentAck, serverSocket, clientIp, clientPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int countSeqNum = 0;

    //Method for sending back acknowledgement for received packet
    public void SendBack(boolean fin) {
        Dgram newPacket = new Dgram();
        countSeqNum ++;
        try {
        int timeOutVal = 1000;
            serverSocket.setSoTimeout(timeOutVal);
            //forming acknowledgemet packet and sending back
            newPacket.ack = true;
            // newPacket.setSeqNum(server_isn);
            System.out.println("receivedSeqNum: " + receivedSeqNum);
            receivedSeqNum += Math.pow(2, 10);
            newPacket.setAckNum(receivedSeqNum);
            DateFormat dateFormat = new SimpleDateFormat("SSS");
            Date date = new Date();
            writer.println(dateFormat.format(date) + " " + receivedSeqNum);
            if (fin) {
                newPacket.setFin();
            }
            System.out.println("Sent ack: " + receivedSeqNum);
            Dgram.sendpckt(newPacket, serverSocket, clientIp, clientPort);
            lastSentAck = newPacket;
            // server_isn += Math.pow(2, 10);
            //receivedSeqNum += Math.pow(2, 10);
        } catch (IOException e) {
            if (!endConnection) listen();
            System.out.println("SendAck Timeout");
        }
    }

    public static void main(String[] args) {
        Server serverSocket = new Server(6565);
        serverSocket.accept();
        serverSocket.listen();
        System.out.println("Connection Closed!" + serverSocket.countSeqNum);
    }
}
