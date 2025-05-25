package fr.uge.tools.object;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import fr.uge.tools.Reader;import fr.uge.tools.object.ListReader.X;

public class ListeningAddressReader implements Reader<X> {
  
  
    public record LISTENING_ADDRESS(byte code, ByteBuffer bytes, int port) implements X {
      
      public ByteBuffer asBuffer() {
        var copyBytes = bytes.duplicate();
            
        var bb = ByteBuffer.allocate(1 + copyBytes.remaining() + Integer.BYTES);
        bb.put(code).put(copyBytes).putInt(port);
        bb.flip();
        return bb;
      }
      
      public InetSocketAddress asInetSocketAddress() throws UnknownHostException {        
        var hostname = InetAddress.getByAddress(bytes.array());
        return new InetSocketAddress(hostname, port);
      }
    };

    private enum State {
        DONE, ERROR, WAITING_FOR_CODE, WAITING_FOR_BUFFER, WAITING_FOR_PORT
    };


    private State state = State.WAITING_FOR_CODE;
    private ByteBuffer internalBuffer; // write-mode
    
    private LISTENING_ADDRESS value;
    
    private OpcodeReader codeReader = new OpcodeReader();
    private IntReader portReader = new IntReader();
    private byte code;
    private int port;
    
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_CODE) {
          var statut = codeReader.process(buffer);
          if (statut == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (statut == ProcessStatus.DONE) {
            code = codeReader.get();
            
            if (code != 4 && code != 16) {
              state = State.ERROR;
              return ProcessStatus.ERROR;
            }    
            state = State.WAITING_FOR_BUFFER;
            internalBuffer = ByteBuffer.allocate(code);     
          }
        }
        
                
        if (state == State.WAITING_FOR_BUFFER) {
          buffer.flip();
          try {
              if (buffer.remaining() <= internalBuffer.remaining()) {
                  internalBuffer.put(buffer);
              } else {
                  var oldLimit = buffer.limit();
                  buffer.limit(internalBuffer.remaining());
                  internalBuffer.put(buffer);
                  buffer.limit(oldLimit);
              }
          } finally {
              buffer.compact();
          }
          if (internalBuffer.hasRemaining()) {
              return ProcessStatus.REFILL;
          }
          state = State.WAITING_FOR_PORT;
        }
        
        
        var statut = portReader.process(buffer);
        if (statut == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        if (statut == ProcessStatus.DONE) {
          port = portReader.get();
          if (port < 0 || port > 65_535) {
            state = State.ERROR;
            return ProcessStatus.ERROR;
          }        
        }
        
        state = State.DONE;
        internalBuffer.flip();
        value = new LISTENING_ADDRESS(code, internalBuffer, port);
        return ProcessStatus.DONE;
    }

    @Override
    public LISTENING_ADDRESS get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_CODE;
        portReader.reset();
        codeReader.reset();
    }
}