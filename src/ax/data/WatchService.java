/*
      pbox - personnal/private cloud box
    Copyright (C) 2014 <Fabien Bavent>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package ax.data;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

public class WatchService implements Runnable
{
  private DirectoryMirror dirMirror;

  private java.nio.file.WatchService watcher;
  private Map<WatchKey,Path> keys;
  private Path topDir;


  @SuppressWarnings("unchecked")
  static <T> WatchEvent<T> cast(WatchEvent<?> event) {
      return (WatchEvent<T>)event;
  }
  /**
   * Creates a WatchService and registers the given directory
   */
  public WatchService (DirectoryMirror dm)
      throws IOException
  {
    this.dirMirror = dm;

    this.watcher = FileSystems.getDefault().newWatchService();
    this.keys = new HashMap<WatchKey,Path>();
    this.topDir = dm.getTopDir();

    System.out.format("[Trace] Scanning %s ...\n", topDir);
    registerDirs(topDir);
  }

  /**
   * Register the given directory, and all its sub-directories, with the
   * WatchService.
   */
  private void registerDirs(Path start) 
      throws IOException 
  {
    // register directory and sub-directories
    Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
          throws IOException
      {
        if (dir.startsWith(Paths.get(topDir + "/.pbox"))) {
          return FileVisitResult.CONTINUE;
        }

        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        Path prev = keys.get(key);
        if (prev == null) {
          register (dir);
        } else if (!dir.equals(prev)) {
          update (prev, dir);  
        }
      

        keys.put(key, dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Process all events for keys queued to the watcher
   */
  public void processEvents() {
    for (;;) {

      // wait for key to be signalled
      WatchKey key;
      try {
        key = watcher.take();
      } catch (InterruptedException x) {
        return;
      }

      Path dir = keys.get(key);
      if (dir == null) {
        System.err.println("WatchKey not recognized!!");
        continue;
      }

      for (WatchEvent<?> event: key.pollEvents()) {
        WatchEvent.Kind kind = event.kind();

        // TBD - provide example of how OVERFLOW event is handled
        if (kind == OVERFLOW) {
            continue;
        }

        // Context for directory entry event is the file name of entry
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = dir.resolve(name);
        child = Paths.get (child.toString().replace(topDir.toString(), "."));

        if (child.startsWith(Paths.get("./.pbox"))) {
          continue;
        }

        // System.out.format("%s: %s\n", event.kind().name(), child);
        switch (event.kind().name()) {
          case "ENTRY_CREATE":
            this.create (child);
            break;
          case "ENTRY_DELETE":
            this.delete (child);
            break;
          case "ENTRY_MODIFY":
            this.modify (child);
            break;

        }
        

        // if directory is created, and watching recursively, then
        // register it and its sub-directories
        if (kind == ENTRY_CREATE) {
          try {
            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
              registerDirs(child);
            }
          } catch (IOException x) {
            // ignore to keep sample readable
          }
        }
      }

      // reset key and remove from set if directory no longer accessible
      boolean valid = key.reset();
      if (!valid) {
        keys.remove(key);

        // all directories are inaccessible
        if (keys.isEmpty()) {
            break;
        }
      }
    }
  }

  private void listFilesForFolder(Path dir) 
  {
    create (dir);
    File folder = new File(dir.toString());
    for (File fileEntry : folder.listFiles()) {
      if (fileEntry.isDirectory()) {
        // listFilesForFolder(fileEntry);
      } else {
        Path file = Paths.get(dir.toString() + "/" + fileEntry.getName());

        create (file);
        /* modify (file);*/
      }
    }
  }

  public void register (Path path) 
  {
    listFilesForFolder(path);
  }

  public void update (Path prev, Path path) 
  {
    // Not implemented, shortcut for rename !
  }

  public void modify (Path path) 
  {
    // System.out.format ("MODIFY %s\n", path);
    path = Paths.get (path.toString().replace(this.topDir.toString(), "."));
    // System.out.format ("[Debug] Named choose is: %s\n", path);
    FileEntry fpEn = this.dirMirror.dataService.getFileEntry (path);

    if (fpEn == null) {
      fpEn = new FileEntry(path, this.topDir);
      this.dirMirror.dataService.addFileEntry (fpEn, path);

    } else {
      this.dirMirror.dataService.updateFileEntry (fpEn, path);
      // System.out.format("[Debug] create but already exist ! -> %s\n", path);
    }
  }

  public void create (Path path) 
  {
    // Act like a modification ()
    modify (path);
  }

  public void delete (Path path) 
  {
    path = Paths.get (path.toString().replace(this.topDir.toString(), "."));
    FileEntry fpEn = this.dirMirror.dataService.getFileEntry (path);
    if (fpEn != null) {
      this.dirMirror.dataService.rmLocalFileEntry (fpEn, path);
    }
  }

  public void run () 
  {
    Thread.currentThread().setName ("WatchService["+this.topDir.toString()+"]");
    System.out.println ("[Trace] WatchService started");
    this.processEvents();
    System.out.println ("[Trace] WatchService stopped");
  }
}
