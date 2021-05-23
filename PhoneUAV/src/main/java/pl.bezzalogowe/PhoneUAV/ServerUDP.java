package pl.bezzalogowe.PhoneUAV;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerUDP {
    /* https://www.reddit.com/r/androiddev/comments/2tm4lj/udp_sending_and_receiving_androidjava/ */

    MainActivity main;
    InetAddress client = null;
    int port = 6000;
    DatagramSocket dsocket;

    public ServerUDP(MainActivity argActivity) {
        main = argActivity;
    }

    /* https://javarevisited.blogspot.com/2013/02/combine-integer-and-string-array-java-example-tutorial.html */
    public static byte[] combine(byte[] a, byte[] b) {
        int length = a.length + b.length;
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * number:
     * 1 - accelerometer,
     * 2 - barometric altitude
     * 3 - GPS altitude
     * 16 - rudder trim
     * 17 - elevator trim
     */

    public void send(byte number, boolean value) {
        /** Sends a byte and a '0' character for false or '1' for true. */

        try {
            byte message;
            if (value) {
                message = 0x31;
            } else {
                message = 0x30;
            }
            byte[] buffer = {number, message};

            DatagramPacket packet = new DatagramPacket(buffer, 2 /*buffer.length*/, client, port);
            dsocket.send(packet);
        } catch (NullPointerException e) {
            //System.err.println(e);
        } catch (Exception e) {
            System.err.println("One byte feedback NOT sent to " + client.getHostName() + ": " + e);
        }
    }


public void sendInteger(byte number, int value) {
    /** Sends a byte and an integer. */

    if (dsocket != null) {
        try {
            byte[] buffer = {number,
                    (byte) (value >> 24),
                    (byte) (value >> 16),
                    (byte) (value >> 8),
                    (byte) (value >> 0)};

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
            dsocket.send(packet);
        }
        catch (NullPointerException e) {
            System.err.println(e);
        }
        catch (Exception e) {
            System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
        }
    }
}

    public void send(byte number, short value) {
        /** Sends a byte and a short. */

        if (dsocket != null) {
            try {
                byte[] buffer = {number,
                        (byte) (value >> 8),
                        (byte) (value >> 0)};

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
                dsocket.send(packet);
            } catch (NullPointerException e) {
                //System.err.println(e);
            } catch (Exception e) {
                System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
            }
        }
    }

    public void send(byte number, short value1, short value2) {
        /** Sends a byte and two shorts. */

        if (dsocket != null) {
            try {
                byte[] buffer = {number,
                        (byte) (value1 >> 8),
                        (byte) (value1 >> 0),
                        (byte) (value2 >> 8),
                        (byte) (value2 >> 0)};

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
                dsocket.send(packet);
                //System.out.println("Feedback sent to " + client.getHostName());
            } catch (NullPointerException e) {
                //System.err.println(e);
            } catch (Exception e) {
                System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
            }
        }
    }

    public void send(byte number, float value) {
        /** Sends a byte and a float. */
        /** https://stackoverflow.com/questions/14308746/how-to-convert-from-a-float-to-4-bytes-in-java */

        try {
            int bits = Float.floatToIntBits(value);
            byte[] buffer = {number,
                    (byte) (bits & 0xff),
                    (byte) ((bits >> 8) & 0xff),
                    (byte) ((bits >> 16) & 0xff),
                    (byte) ((bits >> 24) & 0xff)};
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
            dsocket.send(packet);
        } catch (NullPointerException e) {
            //System.err.println(e);
        } catch (Exception e) {
            System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
        }
    }

    public void send(byte number, float[] value) {
        /** Sends a byte and an array of floats. */
        /** https://stackoverflow.com/questions/14308746/how-to-convert-from-a-float-to-4-bytes-in-java */

        byte[] buffer = new byte[1 + 4 * value.length];

        try {
            buffer[0] = number;
            for (int i = 0; i < value.length; i++) {
                int bits = Float.floatToIntBits(value[i]);
                buffer[1 + i * 4] = (byte) (bits & 0xff);
                buffer[2 + i * 4] = (byte) ((bits >> 8) & 0xff);
                buffer[3 + i * 4] = (byte) ((bits >> 16) & 0xff);
                buffer[4 + i * 4] = (byte) ((bits >> 24) & 0xff);
            }

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
            dsocket.send(packet);
        } catch (NullPointerException e) {
            //System.err.println(e);
        } catch (Exception e) {
            System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
        }
    }

    public void send(byte number, double[] coordinates) {
        /** Sends a byte and an array of doubles. */

        try {
            byte[] header = {number};
            byte[] buffer = combine(header, main.locObject.doubleArray2Bytes(coordinates));

        /*
        System.out.print("Buffer: ");
        for (int i=0; i< buffer.length; i++)
        {
        System.out.print("["+buffer[i]+"]");
        }
        System.out.print(" ("+buffer.length+")\n");
        */
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, client, port);
            dsocket.send(packet);
        } catch (NullPointerException e) {
            //System.err.println(e);
        } catch (Exception e) {
            System.err.println("Feedback NOT sent to " + client.getHostName() + ": " + e.toString());
        }
    }

    public void startServer() {
        Thread serverThread = new Thread(new SockThread());
        serverThread.start();
    }

    public void stopServer() {
        dsocket.close();
    }

    class SockThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {
            try {
                int port = 6000;
                // Create a socket to listen on the port.
                dsocket = new DatagramSocket(port);

                // Create a buffer to read datagrams into. If a
                // packet is larger than this buffer, the
                // excess will simply be discarded!
                byte[] longdata = new byte[0];
                byte[] buffer = new byte[32];

                // Create a packet to receive data into the buffer
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // Now loop forever, waiting to receive packets and printing them.
                while (!Thread.currentThread().isInterrupted()) {
                    // Wait to receive a datagram
                    dsocket.receive(packet);

                    // Convert the contents to a string, and display them
                    /* String msg = new String(buffer, 0, packet.getLength());
                    System.out.println(packet.getAddress().getHostName() + ": " + msg); */

                    if (client == null) {
                        client = packet.getAddress();
                    }

                    System.out.println("Received " + packet.getLength() + " bytes from " + packet.getAddress().getHostName() + ".");
                    longdata = packet.getData();
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(longdata, 0, data, 0, packet.getLength());
                    main.inputObject.process(data);
                    // Reset the length of the packet before reusing it.
                    packet.setLength(buffer.length);
                }
            } catch (Exception e) {
                //System.err.println(e);
            }
        }
    }
}