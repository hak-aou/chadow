package fr.uge.tools;

import fr.uge.tools.object.ChunkReader.CHUNK;
import fr.uge.tools.object.FileReader.FILE;

import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.TreeMap;
  
public class FileInformation {
  private final FILE file;
  private final HashMap<Integer, CHUNK> mapChunk = new HashMap<>();
  private final int nbNumber;
  private int currentNumber = 0;
  private static final int CHUNK_MAX_SIZE = 1024;
  private final BitSet bitset;
  private int cmpt = 0;
  private final int nbIsa;
  private int nbRefusal = 0;
  private static final String DIR_NAME = "download";
  
  
  public static void createEmptyFile(String filename) throws IOException {
    var dirPath = Path.of(DIR_NAME);
    if (!Files.exists(dirPath)) {
      Files.createDirectory(dirPath);
    }
    
    var file = new File(DIR_NAME + "/" + filename);
    file.createNewFile();
  }
  
  public static CHUNK getChunkInFile(String pathname, int chunkNumber) throws IOException {
    var path = Path.of(pathname);
    
    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
      var bb = ByteBuffer.allocate(CHUNK_MAX_SIZE);
      var pos = chunkNumber * CHUNK_MAX_SIZE;
      fc.position(pos);
      
      while (bb.hasRemaining()) {
        if (fc.read(bb) == -1) break; // renvoie -1 si on a atteint la fin du fichier
      }
      bb.flip();
      return new CHUNK(bb);
    }
  }
  
  
  public FileInformation(FILE file, int nbIsa){
    this.file = file;
    nbNumber = getLastChunkNumber() + 1;
    bitset = new BitSet(nbNumber);
    this.nbIsa = nbIsa;
  }
  
  public void getARefusal() {
    nbRefusal++;
  }
  
  public boolean everyoneRefusedToShareTheFile() {
    return nbRefusal >= nbIsa;
  }
  
  private int getLastChunkNumber() {
    return (int) (Math.ceil((double) file.size() / CHUNK_MAX_SIZE) - 1);
  }
  
  public void addChunk(int chunkNumber, CHUNK chunk) {
    if (!bitset.get(chunkNumber)) {
      mapChunk.put(chunkNumber, chunk);
      bitset.set(chunkNumber);
      cmpt++;
    }
  }
  
  public int nextChunkNumber() {
    while(bitset.get(currentNumber)) {
      currentNumber++;
    }
    return currentNumber++ % nbNumber;
  }
  
  
  public boolean canMergeChunksIntoFile() {
    return cmpt >= nbNumber;
  }
  
  
  public void mergeChunksIntoFile() throws IOException {    
//    var path = Path.of(file.filename());
    
    var dirPath = Path.of(DIR_NAME);
    if (!Files.exists(dirPath)) {
      Files.createDirectory(dirPath);
    }
    
    var path = Path.of(DIR_NAME + "/" + file.filename());
    
    try (var outChannel = FileChannel.open(path, WRITE, CREATE, TRUNCATE_EXISTING)) {
      var sortedMap = new TreeMap<>(mapChunk);
      
      for (CHUNK chunk : sortedMap.values()) {
        outChannel.write(chunk.payload());
      }
    }
  }
}

