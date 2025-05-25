package fr.uge.client;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Test {
  public static void main(String[] args) throws IOException {
//    var pathName = "./README.md";
//    System.out.println(pathName);
//    
//    var path = Path.of(pathName);
//    System.out.println(path);
//    
//    
//    if (Files.exists(path)) {
//      System.out.println("yes");
//    }
//    
//    
//    
//    var p = Path.of("").toAbsolutePath();
//    System.out.println(p);
    
    
//    int cmpt = 1;
//    int a = cmpt++ % 2;
//    System.out.println(a);
//    System.out.println(cmpt++);
//    System.out.println(cmpt);
  
    
//    var file = new FileInputStream("./file.md");
//    var bb = ByteBuffer.allocate(1024);
//   
//    for (var i = 0; i < 1024; i++) {
//      var bit = file.read();
//      if (bit == -1) {
//        break;
//      }
//      bb.put((byte)bit);
//    }
//    
//    bb.flip();
//    
//    Path path = Paths.get("./file_test.md");
//    Files.write(path, bb.array());
//    
//    System.out.println(bb.remaining());
//    while(bb.hasRemaining()) {
//      System.out.println(bb.get());
//    }
//
//    file.close();
    
    
    
//    var path = Path.of("./file.md");
//    
//    FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
//    var bb = ByteBuffer.allocate(1024);
//    var pos = 0;
//    fc.position(pos);
//    
//    while (bb.hasRemaining()) {
//      if (fc.read(bb) == -1) break; // renvoie -1 si on a atteint la fin du fichier
//    }
//    bb.flip();
////    System.out.println(bb.remaining());
////    while(bb.hasRemaining()) {
////      System.out.println(bb.get());
////    }
//    fc.close();
//    
//    
//    var path2 = Path.of("./file_test.md");
//    var outChannel = FileChannel.open(path2, WRITE, CREATE, TRUNCATE_EXISTING);
//    outChannel.write(bb);
    
    
    
    
//    var size = 1025;
//    var newBuffer = ByteBuffer.allocate(size);
//    for(var i = 0; i < size; i++) {
//      newBuffer.put((byte)1);
//    }
//    newBuffer.flip();
//    
//    var path2 = Path.of("./file_test.md");
//    var outChannel = FileChannel.open(path2, WRITE, CREATE, TRUNCATE_EXISTING);
//    outChannel.write(newBuffer);
//    
//    
//    FileChannel fc = FileChannel.open(path2, StandardOpenOption.READ);
//    var bb = ByteBuffer.allocate(1024);
//    var pos = 1024;
//    fc.position(pos);
//    
//    while (bb.hasRemaining()) {
//      if (fc.read(bb) == -1) break; // renvoie -1 si on a atteint la fin du fichier
//    }
//    bb.flip();
//    System.out.println(bb.remaining());
//    while(bb.hasRemaining()) {
//      System.out.println(bb.get());
//    }
//    fc.close();
    
    
//    File file = new File("demo1.txt");
//    file.createNewFile();
    
    
    
//    var pathName = "./test/file.md";
//    var filename = Path.of(pathName).getFileName().toString();
//    var filename2 = new File(pathName).getName();
//    
//    System.out.println(filename);
//    System.out.println(filename2);
    
    var name = "download";
    var dirPath = Path.of(name);
    if (!Files.exists(dirPath)) {
      Files.createDirectory(dirPath);
    }
    
    
    
    
  }
}
