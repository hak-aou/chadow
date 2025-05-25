package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.FileReader.FILE;

public final class RequestListSharerOrProxyReader implements Reader<RequestListSharerOrProxyReader.RequestListSharerOrProxy>,Trame {
  
    public record RequestListSharerOrProxy(FILE file) {
      public ByteBuffer asBuffer() {
        return file.asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILE
    }

    private State state = State.WAITING_FOR_FILE;
    private RequestListSharerOrProxy value;
    
  
    private FileReader fr = new FileReader();
    private FILE file;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_FILE) {
          var status = fr.process(buffer);
          switch (status) {
            case DONE:           
              file = fr.get();
              state = State.DONE;
              fr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        value = new RequestListSharerOrProxy(file);
        return ProcessStatus.DONE;
    }

    @Override
    public RequestListSharerOrProxy get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_FILE;
    }
}