package pl.bezzalogowe.PhoneUAV;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerTCP {
    /**
     * <p>
     * Size of Ethernet frame: 24 Bytes
     * Size of IPv4 Header (without any options): 20 Bytes
     * <p>
     * Size of TCP Header (without any options): 20 Bytes
     * So total size of empty TCP datagram: 24 + 20 + 20 = 64 Bytes
     * <p>
     * Size of UDP header: 8 bytes
     * So total size of empty UDP datagram: 24 + 20 + 8 = 52 Bytes
     * </p>
     */

    private static final String TAG = "network";
    public int server_port = 6000;
    public OutputStream output;
    MainActivity main;
    ServerSocket serverSocket;

    public ServerTCP(MainActivity argActivity) {
        main = argActivity;
    }


    public void displayAddress() {
        if (main.networkObject.getLocalIpAddressString() != null) {
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, main.networkObject.getLocalIpAddressString() + ":" + server_port + " \n"));
        } else {
            main.text_server.setText("Offline");
        }
    }

    public void send(int number, int value) {
        /** Sends one integer */

        if (output != null) {
            try {

                byte[] buffer = {(byte) number,
                        (byte) (value >> 24),
                        (byte) (value >> 16),
                        (byte) (value >> 8),
                        (byte) (value >> 0)};
/*
>>	signed right shift
>>>	unsigned right shift
*/
                output.write(buffer);
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Feedback: " + Integer.toString(number) + ", " + Integer.toString(value)));
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Could not send feedback: " + e);
            }
        }
    }

    public void send(byte number, short value1, short value2) {
        /** Sends two shorts */

        if (output != null) {
            try {
                byte[] buffer = {number,
                        (byte) (value1 >> 8),
                        (byte) (value1 >> 0),
                        (byte) (value2 >> 8),
                        (byte) (value2 >> 0)};
                output.write(buffer);
                main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Feedback: " + Integer.toString(number) + ", " + Short.toString(value1) + ", " + Short.toString(value2)));
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Could not send feedback: " + e);
            }
        }
    }

    public void send(byte number, float value) {
        /** Sends a float. */
        /** https://stackoverflow.com/questions/14308746/how-to-convert-from-a-float-to-4-bytes-in-java */

        try {
            int bits = Float.floatToIntBits(value);
            byte[] buffer = {number,
                    (byte) (bits & 0xff),
                    (byte) ((bits >> 8) & 0xff),
                    (byte) ((bits >> 16) & 0xff),
                    (byte) ((bits >> 24) & 0xff)};
            output.write(buffer);
            main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "Feedback: " + Integer.toString(number) + ", " + Float.toString(value)));
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void startServer() {
        Thread serverThread = new Thread(new SockThread());
        serverThread.start();
    }

    class CommunicationThread implements Runnable {
        private InputStream input;

        public CommunicationThread(Socket clientSocket) {

            try {
                this.input = clientSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Comm thread error: " + e);
            }
        }

        @SuppressWarnings("all")
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                //byte[] data = {0, 0, 0, 0, 0};
                //byte[] data = new byte[5];
                byte[] data = new byte[0];
                byte[] buff = new byte[64];
                int bytesread = 0;

                try {
                    // http://stackoverflow.com/questions/5690954/
                    if ((bytesread = input.read(buff, 0, buff.length)) > 0) {
                        byte[] tbuff = new byte[data.length + bytesread];
                        System.arraycopy(data, 0, tbuff, 0, data.length);
                        System.arraycopy(buff, 0, tbuff, data.length, bytesread);
                        data = tbuff;
                        Log.d(TAG, "Bytes read: " + bytesread);
                        main.inputObject.process(data);
                    } else if (bytesread == 0) {
                        Log.d(TAG, "Bytes read: " + bytesread);
                    } else {
                        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, "End of stream"));
                    }

                } catch (IOException e) {
                    //e.printStackTrace();
                    //Log.d(TAG, "IOException:" + e);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                    Log.d(TAG, "IndexOutOfBoundsException:" + e);
                }
            }
        }
    }

    @SuppressWarnings("all")
    class SockThread implements Runnable {
        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(server_port);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Server socket error: " + e);
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();

                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();

                    //FIXME: Move to own thread or CommunicationThread?
                    try {
                        output = socket.getOutputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d(TAG, "Output stream error: " + e);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println(e);
                }
            }
        }
    }
}