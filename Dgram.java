import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Dgram implements Serializable {
    int seqNum;
    int ackNum;
    boolean ack = false;
    boolean finish = false;
    boolean sync = false;
    static int prec;
    static InetAddress rec;

    Dgram() {
    }
    Dgram(int seqNum, boolean syn, boolean ack) {
        this.seqNum = seqNum;
        this.sync = syn;
        this.ack = ack;
    }

    public Dgram(boolean b, boolean b1, int i) {
        this.sync = b;
        this.ack = b1;
        this.ackNum = i;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public void setAckNum(int ackNum) {
        this.ackNum = ackNum;
    }

    public void setFin() {
        finish = true;
    }

    public int getAckNum() {
        return ackNum;
    }

    public static void sendpckt(Dgram packet, DatagramSocket socket, InetAddress clientIp, int clientPort) throws IOException {
        DatagramPacket sendPacket;
        byte[] sendData;
        ByteArrayOutputStream byteSendStream = new ByteArrayOutputStream(1000);
        ObjectOutputStream objectSendStream = new ObjectOutputStream(new BufferedOutputStream(byteSendStream));
        objectSendStream.flush();
        objectSendStream.writeObject(packet);
        objectSendStream.flush();
        objectSendStream.close();
        byteSendStream.close();
        sendData = byteSendStream.toByteArray();
        sendPacket = new DatagramPacket(sendData, sendData.length, clientIp, clientPort);
        socket.send(sendPacket);
    }

    public static Dgram createSeg(DatagramSocket socket) throws IOException, ClassNotFoundException {
        //intializing paramenter for receiving packet
        byte[] reqRecvData = new byte[20000];
        DatagramPacket recvPacket;
        ByteArrayInputStream byteRecvStream;
        ObjectInputStream objectRecvStream;
        recvPacket = new DatagramPacket(reqRecvData, reqRecvData.length);
        Dgram newRcvdPacket;
        socket.receive(recvPacket);
        System.out.println("Packet Received!");
        rec = recvPacket.getAddress();
        prec = recvPacket.getPort();
        byteRecvStream = new ByteArrayInputStream(reqRecvData);
        objectRecvStream = new ObjectInputStream(new BufferedInputStream(byteRecvStream));
        newRcvdPacket = (Dgram) objectRecvStream.readObject();
        return newRcvdPacket;
    }
}
