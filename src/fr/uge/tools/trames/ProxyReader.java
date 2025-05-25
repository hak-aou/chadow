package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.object.IntReader;
import fr.uge.tools.object.ListeningAddressReader;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;

public final class ProxyReader implements Reader<ProxyReader.Proxy>, Trame {

  
    public record Proxy(int tokenSrc, LISTENING_ADDRESS dst, int tokenDst) {
      
      public ByteBuffer asBuffer() {
        var dstBb = dst.asBuffer();
        
        var bb = ByteBuffer.allocate(2 * Integer.BYTES + dstBb.remaining());
        bb.putInt(tokenSrc);
        bb.put(dstBb);
        bb.putInt(tokenDst);
        bb.flip();
        return bb;
      }
    }
    

    
    private enum State {
        DONE, ERROR, WAITING_FOR_TOKEN_SRC, WAITING_FOR_DST, WAITING_FOR_TOKEN_DST
    }

    private State state = State.WAITING_FOR_TOKEN_SRC;
    private Proxy value;
    
  
    private IntReader intReader = new IntReader();
    private ListeningAddressReader laReader = new ListeningAddressReader();
    private int tokenSrc;
    private LISTENING_ADDRESS dst;
    private int tokenDst;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_TOKEN_SRC) {
          var status = intReader.process(buffer);
          if (status == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (status == ProcessStatus.DONE) {
            tokenSrc = intReader.get();
            state = State.WAITING_FOR_DST;
            intReader.reset();
          }
        }
         
        if (state == State.WAITING_FOR_DST) {
          var status = laReader.process(buffer);
          switch (status) {
            case DONE:           
              dst = laReader.get();
              state = State.WAITING_FOR_TOKEN_DST;
              laReader.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        var status = intReader.process(buffer);
        if (status == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        if (status == ProcessStatus.DONE) {
          tokenDst = intReader.get();
        }
        
        state = State.DONE;
        value = new Proxy(tokenSrc, dst, tokenDst);
        return ProcessStatus.DONE;
    }

    @Override
    public Proxy get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_TOKEN_SRC;
        intReader.reset();
    }
}