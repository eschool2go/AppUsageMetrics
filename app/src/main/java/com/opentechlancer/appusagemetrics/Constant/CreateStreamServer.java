package com.opentechlancer.appusagemetrics.Constant;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import okhttp3.Headers;

/**
 * Created by pr0 on 4/4/17.
 */

public class CreateStreamServer {
    private static final boolean debug = false;

    private final ServerSocket serverSocket;
    private Thread mainThread;

    private int i = 0;

    Context context;
    List<String> events = new ArrayList<>();

    /**
     * Some HTTP response status codes
     */
    private static final String
            HTTP_BADREQUEST = "400 Bad Request",
            HTTP_416 = "416 Range not satisfiable",
            HTTP_INTERNALERROR = "500 Internal Server Error";

    private static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException e) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start embedded Jetty HTTP Server on");
    }

    public CreateStreamServer(final Context context) throws IOException {

        this.context = context;

        int port = findFreePort();
        serverSocket = new ServerSocket(port);
        SharedPreferencesDB.getInstance(context).setPreferenceIntValue("port", port);

        Log.e("free port", port + "");

        mainThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Socket accept = serverSocket.accept();
                        accept.setKeepAlive(true);

                        ++i;

                        Log.e("adding", "connection");
                        new HttpSession(accept);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });
        mainThread.setName("Stream over HTTP");
        mainThread.setDaemon(true);
        mainThread.start();
    }

    private class HttpSession implements Runnable {
        private RandomAccessFile is;
        private final Socket socket;
        private File file;

        HttpSession(Socket s) {
            socket = s;
            Log.e("Stream over localhost:", "serving request on " + s.getInetAddress());

            Thread t = new Thread(this, "Http response");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void run() {
            handleResponse(socket);
        }

        private void openInputStream() throws IOException {
            // openRandomAccessInputStream must return RandomAccessInputStream if file is ssekable, null otherwise
            is = new RandomAccessFile(file, "r");
        }

        private void handleResponse(Socket socket) {
            try {
                InputStream inS = socket.getInputStream();
                if (inS == null)
                    return;
                byte[] buf = new byte[8192];
                int rlen = inS.read(buf, 0, buf.length);
                if (rlen <= 0)
                    return;

                // Create a BufferedReader for parsing the header.
                ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
                BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));

                final Properties pre = new Properties();

                // Decode the header into params and header java properties
                if (!decodeHeader(socket, hin, pre))
                    return;

                JSONObject obj= new JSONObject(pre.getProperty("events"));
                JSONArray array = obj.getJSONArray("events");

                Log.e("arr l", array.length() + "");
                StringBuilder intArr = new StringBuilder();
                intArr.append("[");

                for(int i = 0; i < array.length(); ++i) {
                    JSONObject object = array.getJSONObject(i);
                    intArr.append(object.getInt("id"));

                    if(i < array.length() - 1)
                        intArr.append(",");
                }

                intArr.append("]");

                Properties head = new Properties();
                head.put("status", "success");
                head.put("recieve", intArr.toString());

                sendResponse(socket, "text/plain", head,  null);
                inS.close();
                Log.e("stream finished", "close");
            } catch (IOException ioe) {
                    ioe.printStackTrace();
                try {
                    sendError(socket, HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
                } catch (Throwable t) {
                }
            } catch (InterruptedException ie) {
                // thrown by sendError, ignore and exit the thread
                    ie.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private boolean decodeHeader(Socket socket, BufferedReader in, Properties pre) throws InterruptedException {
            try {
                // Read the request line
                String inLine = in.readLine();
                if (inLine == null)
                    return false;
                StringTokenizer st = new StringTokenizer(inLine);
                int count = st.countTokens();

                if (!st.hasMoreTokens())
                    sendError(socket, HTTP_BADREQUEST, "Syntax error");

                String method = st.nextToken();
                if (!method.equals("POST"))
                    return false;

                if (!st.hasMoreTokens())
                    sendError(socket, HTTP_BADREQUEST, "Missing URI");
                else {
                    String next = st.nextToken();
                    Log.e("request", inLine);
                }

                while (true) {
                    String line = in.readLine();
                    if (line == null)
                        break;
                    //            if(debug && line.length()>0) BrowserUtils.LOGRUN(line);
                    int p = line.indexOf(':');
                    if (p < 0)
                        continue;
                    final String atr = line.substring(0, p).trim().toLowerCase();
                    final String val = line.substring(p + 1).trim();

                    if(atr.contains("events")) {
                        Log.e(atr, val);

                        Intent i = new Intent("events");
                        i.putExtra("val", val);

                        LocalBroadcastManager.getInstance(context).
                                sendBroadcast(i);

                        pre.put("events", atr +":" + val);

                        events.add(val);
                        SharedPreferencesDB.getInstance(context).setPreferenceListValue("events",
                                events);
                    }
                }
            } catch (IOException ioe) {
                sendError(socket, HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            }
            return true;
        }
    }


    /**
     * @param fileName is display name appended to Uri, not really used (may be null), but client may display it as file name.
     * @return Uri where this stream listens and servers.
     */
    public Uri getUri(String fileName) {
        int port = serverSocket.getLocalPort();
        String url = "http://localhost:" + port;
        if (fileName != null)
            url += '/' + URLEncoder.encode(fileName);
        return Uri.parse(url);
    }

    public void close() {
        Log.e("closing", "stream");
        try {
            serverSocket.close();

            mainThread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an error message as a HTTP response and
     * throws InterruptedException to stop further request processing.
     */
    private void sendError(Socket socket, String status, String msg) throws InterruptedException {
        sendResponse(socket, "text/plain", null, msg);
        throw new InterruptedException();
    }


    private void sendMetaDataRes(Socket socket, long totalD, long totalB, String name) throws InterruptedIOException {
        sendMetaData(socket, totalD, totalB, name);
        throw new InterruptedIOException();
    }

    private void sendStatusRes(Socket socket) throws InterruptedIOException {
        sendStatus(socket);
        throw  new InterruptedIOException();
    }

    private static void copyStream(RandomAccessFile in, OutputStream out, byte[] tmpBuf, long maxSize) throws IOException {

        while (maxSize > 0) {
            int count = (int) Math.min(maxSize, tmpBuf.length);
            count = in.read(tmpBuf, 0, count);
            if (count < 0)
                break;
            out.write(tmpBuf, 0, count);
            maxSize -= count;
        }
    }

    /**
     * Sends given response to the socket, and closes the socket.
     */
    private void sendResponse(Socket socket, String mimeType,
                              Properties header, String errMsg) {
        try {
            Log.e("inside response", header.getProperty("status"));
            OutputStream out = socket.getOutputStream();
            PrintWriter pw = new PrintWriter(out);

            {
                String retLine = "HTTP/1.0 " + 200 + " \r\n";
                pw.print(retLine);
            }
            if (mimeType != null) {
                String mT = "Content-Type: " + mimeType + "\r\n";
                pw.print(mT);
            }

            pw.print("\r\n");
            pw.print("{\"status\":\"success\"," + "\"recieved\":" + header.get("recieve") + "}");
            pw.flush();
            if (errMsg != null) {
                pw.print(errMsg);
                pw.flush();
            }
            out.flush();
            out.close();
        } catch (IOException e) {
                Log.e("debug", e.getMessage());
        }
    }

    public void sendMetaData(Socket socket, long totalD, long totalB, String name) {
        try {
            String httpResponse = "HTTP/1.1 200 OK\r\n\r\n";
            httpResponse += totalD + ":" + totalB +":" + name;
            socket.getOutputStream().write(httpResponse.getBytes("UTF-8"));
            socket.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                --i;
            } catch (Throwable t) {
            }
        }
    }

   public void sendStatus(Socket socket) {
        try {
            String httpResponse = "HTTP/1.1 200 OK\r\n\r\n";

            socket.getOutputStream().write(httpResponse.getBytes("UTF-8"));
            socket.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                --i;
            } catch (Throwable t) {
            }
        }
    }

    public String getMimeType(File f) {
        String name = f.getAbsolutePath();
        String type = "";

        if(name.toString().lastIndexOf(".") != -1) {
            String ext = name.substring(name.lastIndexOf(".")+1);
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(ext);
        } else {
            type = null;
        }

        return type;
    }

    public String getDeviceIpAddress() {
        try {
            //Loop through all the network interface devices
            for (Enumeration<NetworkInterface> enumeration = NetworkInterface
                    .getNetworkInterfaces(); enumeration.hasMoreElements(); ) {
                NetworkInterface networkInterface = enumeration.nextElement();
                //Loop through all the ip addresses of the network interface devices
                for (Enumeration<InetAddress> enumerationIpAddr = networkInterface.getInetAddresses(); enumerationIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumerationIpAddr.nextElement();
                    //Filter out loopback address and other irrelevant ip addresses
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        //Print the device ip address in to the text view
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("ERROR:", e.toString());
        }

        return "";
    }

    public int getPort() {
        Log.e("podrt", serverSocket.getLocalPort() + "");
        return serverSocket.getLocalPort();
    }

    public Bitmap getAlbumArt(String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(path);
        InputStream inputStream = null;
        if (mmr.getEmbeddedPicture() != null) {
            inputStream = new ByteArrayInputStream(mmr.getEmbeddedPicture());
        }
        mmr.release();

        if(inputStream != null)
            return Bitmap.createScaledBitmap(BitmapFactory.decodeStream(inputStream), 20, 20, true);
        else
            return null;
    }

    public String bimapToString(Bitmap b) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();

        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    public long getTotalDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        //use one of overloaded setDataSource() functions to set your data source
        retriever.setDataSource(context, Uri.fromFile(file));
        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long timeInMillisec = Long.parseLong(time);

        retriever.release();
        return timeInMillisec;
    }

    public long getTotalBytes(File file) {
        return file.length();
    }
}