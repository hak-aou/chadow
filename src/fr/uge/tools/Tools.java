package fr.uge.tools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;

public class Tools {
  public static final Charset UTF8 = StandardCharsets.UTF_8;

  public static ByteBuffer stringToBuffer(String str) {
    var strBb = UTF8.encode(str);
    
    var bb = ByteBuffer.allocate(Integer.BYTES + strBb.remaining());
    bb.putInt(strBb.remaining()).put(strBb);
    bb.flip();
    return bb;
  }
  
//  public static getTrame() {
//  
//  }
  
  
  public static ByteBuffer byteToBuffer(byte code) {
    var bb = ByteBuffer.allocate(1);
    bb.put(code);
    bb.flip();
    return bb;
  }
  
  
  public static LISTENING_ADDRESS sscToListeningAddress(ServerSocketChannel ssc) throws IOException {
    var address = (InetSocketAddress) ssc.getLocalAddress();
    var port = address.getPort();
    var ip = address.getAddress();

    var bytes = ByteBuffer.allocate(1_024);
    bytes.put(ip.getAddress());
    bytes.flip();

    var code = (byte) bytes.remaining();

    return new LISTENING_ADDRESS(code, bytes, port);
  }
}
