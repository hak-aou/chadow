package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.ListReader;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListReader.LIST;

public final class AvailableFilesReader implements Reader<AvailableFilesReader.AvailableFiles>,Trame {
  
    public record AvailableFiles(List<FILE> files) {
      public ByteBuffer asBuffer() {
        return new LIST(files.size(), new ArrayList<>(files)).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_AVAILABLE_FILES
    }

    private State state = State.WAITING_FOR_AVAILABLE_FILES;
    private AvailableFiles value;
    
  
    private ListReader lr = new ListReader(new FileReader());
    private List<FILE> availableFiles;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_AVAILABLE_FILES) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              availableFiles = lr.get().list().stream().map(x -> (FILE) x).toList();
              state = State.DONE;
              lr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        value = new AvailableFiles(availableFiles);
        return ProcessStatus.DONE;
    }

    @Override
    public AvailableFiles get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_AVAILABLE_FILES;
    }
}