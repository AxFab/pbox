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
import java.io.*;
import java.util.*;

public class DataService implements Runnable
{
  private DirectoryMirror dirMirror;
  private Path topDir;

  private Hashtable<Path, FileEntry> entries;
  private HashSet<FileEntry> needUpdate;

  public DataService (DirectoryMirror dm)
  {
    this.dirMirror = dm;
    this.topDir = dm.getTopDir();
    this.entries = new Hashtable<Path, FileEntry> ();
    this.needUpdate = new HashSet<FileEntry> ();
  }

  public FileEntry getFileEntry (Path path)
  {
    synchronized (entries) {
      return entries.get (path);  
    }
  }

  public void updateFileEntry (FileEntry file, Path path)
  {
    file.lock (); // Prevent watcher to mess with it

    if (file.invalidate ()) {
      synchronized (needUpdate) {
        needUpdate.add (file);
      }
    }
    file.unlock();
  }

  public void rmLocalFileEntry (FileEntry file, Path path)
  {
    file.localdelete (this.dirMirror.webService);
    rmFileEntry (file, path);
  }

  public void rmFileEntry (FileEntry file, Path path)
  {
    file.lock (); // Prevent watcher to mess with it

    if (file.delete ()) {
      synchronized (needUpdate) {
        needUpdate.add (file);
      }
    }
    file.unlock();
  }

  public void remove (FileEntry file, Path path)
  {
    synchronized (entries) {
      entries.remove (path);  
    }
  }

  public void addFileEntry (FileEntry file, Path path)
  {
    System.out.format("[Trace] register on db -> %s\n", path);
    file.lock (); // Prevent watcher to mess with it

    synchronized (entries) {
      entries.put (path, file);  
    }

    synchronized (needUpdate) {
      needUpdate.add (file);
    }

    file.unlock();
  }


  public void  processFiles() 
  {
    int max = 300;

    for (;;) {
      FileEntry fp = null;
      Date timer = new Date();
      timer.setTime (timer.getTime() - (3L * 1000L * 1000L));

      synchronized (needUpdate) {
        for (FileEntry e : needUpdate) {
          if (e.somethingToDo(timer) && e.lock ()) {
            fp = e;
            break;
          }
        }
      }

      if (fp != null) {
        if (fp.doSomething (timer, this.dirMirror)) {
          synchronized (needUpdate) {
            needUpdate.remove (fp);
          }
        }
        fp.unlock ();
      }

      if (fp == null || --max <= 0) {
        try {
          Thread.sleep (3500);
          System.out.format ("[Debug] DataService, List %d\n", needUpdate.size());

          synchronized (needUpdate) {
            for (FileEntry e : needUpdate) {
              System.out.format ("  DS:: %s\n", e);
            }
          }
        } catch (InterruptedException ex) {
        }
        max = 300;
        continue;
      }
    }
  }


  public void run () 
  {
    Thread.currentThread().setName ("DataService["+this.topDir.toString()+"]");
    System.out.println ("[Trace] DataService started");
    this.processFiles();
    System.out.println ("[Trace] DataService stopped");
  }

}
