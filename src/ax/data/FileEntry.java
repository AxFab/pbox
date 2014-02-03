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

import ax.io.BeamOut;
import ax.io.BeamIn;
import ax.io.BeamFormatException;
import ax.data.ILoadable;
import ax.data.Store;
import java.util.concurrent.atomic.AtomicInteger;

enum BlobType {
  Unknown, File, Exe, Dir,
}

enum BlobStatus {
  Unknown, Saved, UpdateLocal, UpdateExtern, InStore, Faulted, Loading, Deleted
}

public class FileEntry implements ILoadable
{

  Path        topDir;
  public Path        path;
  String      name; 
  public String      parent;
  public BlobType    type;
  BlobStatus  status;
  long        length;

  public String      hash;

  long        version;

  Date lastUpdate;
  boolean updated;
  AtomicInteger locked;
  Thread lockT;
  StackTraceElement[] lockS;

  public FileEntry (Path path, Path topDir) 
  {
    this.locked = new AtomicInteger(0);
    this.topDir = topDir;
    this.path = path;
    this.version = 0;
    File fp = buildFile ();
    this.name = fp.getName();
    this.status = BlobStatus.UpdateLocal;
    this.lastUpdate = new Date();
    this.lastUpdate.setTime(Long.MAX_VALUE);

    if (fp.isDirectory())
      this.type = BlobType.Dir;
    else if (fp.isFile()) {
      if (fp.canExecute()) 
        this.type = BlobType.Exe;
      else
        this.type = BlobType.File;
    } 
    else 
      this.type = BlobType.Unknown;
  }

  public FileEntry (Path path, String hash, String pHash, long version, String type, Path topDir) 
  {
    this.locked = new AtomicInteger(0);
    this.topDir = topDir;
    this.path = path;
    this.hash = hash;
    this.parent = pHash;
    this.version = version;
    File fp = buildFile ();
    this.name = fp.getName();
    this.status = BlobStatus.UpdateExtern;
    this.type = BlobType.valueOf (type);
  }

  /* === File access ======================================================= */

  private File buildFile () 
  {
    return new File (this.topDir.toString() + "/" + this.path.toString());
  }

  public static File buildFileRecord (Path topDir, String hash) 
  {
    return new File (topDir.toString() + "/.pbox/obj/" + hash.substring(0,2) + "/" + hash.substring(2));
  }

  private File buildRecord () 
  {
    return FileEntry.buildFileRecord (this.topDir, this.hash);
  }

  /* === Locking mechanisms ================================================ */

  public boolean trylock () 
  {
    if (this.locked.getAndSet(1) == 0) 
    {
      /*
      lockT = Thread.currentThread();
      lockS = lockT.getStackTrace();
      */
      return true;
    }
    return false;
  }

  public boolean lock () 
  {
    int m = 50;
    while (this.locked.getAndSet(1) != 0) {
      Thread.yield();
      if (--m < 0) {
        m = 50;
        /*
        System.err.format ("[Err.] Try to get a FileEntry lock at: <%s>\n", 
          Thread.currentThread().getName());
        // new Throwable().printStackTrace();
        Thread.currentThread().dumpStack();
        System.err.format ("[----] Against: <%s>\n", lockT.getName());
        // lockT.dumpStack();
        for(StackTraceElement st : lockS){
          System.err.println(st);
        } */
      }
    }
    
    /*
    lockT = Thread.currentThread();
    lockS = lockT.getStackTrace();
    */
    return true;
  }

  public void unlock () 
  {
    /*
    lockT = null;
    lockS = null; */
    this.locked.set (0);
  }

  /* === File Entry Actions ================================================ */

  public boolean invalidate () 
  {
    if (this.status != BlobStatus.Saved && 
        this.status != BlobStatus.UpdateLocal) {
      System.err.format ("[Warn.] Wrong workflow <invalidate> %s, %s\n", this.status, this.path);
      return false; 
    }

    System.out.format ("[Debug] follow workflow <invalidate, %s>\n", path);

    this.status = BlobStatus.UpdateLocal;
    this.lastUpdate = new Date();
    return true; 
  }

  public void extern (String hash, String parent, long version) 
  {
    if (this.status != BlobStatus.Saved) {
      System.err.format ("[Warn.] Wrong workflow <extern> %s, %s\n", this.status, this.path);
      return; 
    }

    System.out.format ("[Debug] follow workflow <extern, %s>\n", path);

    if (this.hash == hash && this.parent == parent && this.version == version) {

      System.err.format ("[Trace] I'm up to date for %s\n", this.path);
      return;
    } else if (this.hash != parent) {
      System.err.format ("[Warn.] This is a collision\n");
      return;  
    }

    this.hash = hash;
    this.status = BlobStatus.UpdateExtern;
  }

  private void commitSave (String hash, File fTmp) 
      throws IOException
  {
    String subdir = topDir.toString() + "/.pbox/obj/" + hash.substring(0,2) + "/";
    File dir = new File (subdir);
    File dest = new File(subdir + hash.substring(2));

    dir.mkdirs();
    if (dest.exists()) {
      if (fTmp.length() == dest.length()) {
        dest.delete();
      } else {
        System.err.format ("Damn, you got a hash collision on %s\n", hash);
        // TODO safe guard here <Reported for weak propability of hash collision>
        throw new IOException("File exist: hash-collistion");
      }
    }

    if (!fTmp.renameTo(dest)) {
      throw new IOException("Impossible to rename temporary file");
    }
  }

  public void save () 
  {
    if (this.status != BlobStatus.UpdateLocal) {
      System.err.format ("[Warn.] Wrong workflow <save> %s, %s\n", this.status, this.path);
      return; 
    }

    System.out.format ("[Debug] follow workflow <save, %s>\n", path);

    String hash;
    long lg;

    try {
      File fp = this.buildFile ();
      lg = fp.length();

      File fTmp = new File (topDir.toString() + "/.pbox/thread-id.tmp");
      BeamOut bOut = new BeamOut (fTmp);
      bOut.writeOpenObject ("data");
      bOut.writeString ("name", this.path.toString());
      bOut.writeString ("type", this.type.toString());
      bOut.writeString ("parent", this.parent);
      bOut.writeInteger ("version", this.version + 1);

      if (type == BlobType.File || type == BlobType.Exe) {
        bOut.writeFile ("content", fp);
      }
      bOut.writeCloseObject ();
      hash = bOut.commit ();
      bOut.close ();
      commitSave (hash, fTmp);

    } catch (IOException e) {
      System.err.format ("[Error] IOError %s\n", e.getMessage());
      return;
    }

    this.length = lg;
    this.parent = this.hash;
    this.hash = hash;
    this.lastUpdate = new Date();
    this.status = BlobStatus.Saved;
  }

  public void reload (String namespace, WebService ws) 
  {
    if (this.status != BlobStatus.UpdateExtern) {
      System.err.format ("[Warn.] Wrong workflow <reload> %s, %s\n", this.status, this.path);
      return; 
    }

    System.out.format ("[Debug] follow workflow <reload, %s>\n", path);

    this.status = BlobStatus.Loading;

    if (this.length < 64L * 1024L) {
      try {
        if (ws.get (namespace, this.hash)) {
          this.status = BlobStatus.InStore;
        } else {
          this.status = BlobStatus.UpdateExtern;
          // System.out.format ("[Debug] need to retry <reload, %s>\n", path);
        }
      } catch (IOException e) {
        this.status = BlobStatus.UpdateExtern;
        System.err.format ("[Error] %s\n", e.getMessage());
        return;
      }
    } else if (this.length < 128L * 1024L * 1024L) {
      ws.async (namespace, this.hash, this);
    } else {
      ws.peer (namespace, this.hash, this);
    }
  }

  public void laodFinish (boolean success, String message) 
  {
    if (success) {
      this.status = BlobStatus.InStore;
    } else {
      this.status = BlobStatus.UpdateExtern;
      System.err.format ("[Error] %s\n", message);
    }
  }

  public void extract () 
  {
    if (this.status != BlobStatus.InStore) {
      System.err.format ("[Warn.] Wrong workflow <extract> %s, %s\n", this.status, this.path);
      return; 
    }

    System.out.format ("[Debug] follow workflow <extract, %s>\n", path);

    try {
      File out = this.buildFile();
      if (this.type == BlobType.Dir) {
        out.mkdirs();
      } else if (this.type == BlobType.File || this.type == BlobType.Exe) {
        out.getParentFile().mkdirs();

        System.out.format ("[Debug] E, [%s, %s]\n", this.topDir, this.hash);
        File in = this.buildRecord();
        BeamIn bIn = new BeamIn(in);

        String label = bIn.readLabel ();
        bIn.readObject ();
        while ((label = bIn.readLabel ()) != null) {
          switch (label) {
            case "name":
              label = bIn.readString ();
              break;
            case "type":
              label = bIn.readString ();
              break;
            case "parent":
              label = bIn.readString ();
              break;
            case "version":
              long vers = bIn.readInteger ();
              break;
            case "content":
              System.err.format ("[Trace] Re-write file %s\n", out.getPath());
              bIn.readFile (out);
              break;
            default:
              System.err.format("[Debug] Unknown field %s\n", label);
              bIn.readPass ();
              break;
          }
        }
      }
      System.err.format ("[Trace] Done extract file %s -> %s\n", this.hash, out.getPath());
    } catch (BeamFormatException e) {
      System.err.format ("[Error] Wrong format on Beam extraction %s\n", e.getMessage());
      return;
    } catch (IOException e) {
      System.err.format ("[Error] IOError on extract %s\n", e.getMessage());
      return;
    }

    this.status = BlobStatus.Saved;
  }

  public void localdelete (WebService ws) {
    ws.sendDelete (this.path, this.hash, this.version);
  }

  public boolean delete ()
  {
    if (this.status != BlobStatus.Saved && 
        this.status != BlobStatus.UpdateLocal) {
      System.err.format ("[Warn.] Wrong workflow <delete> %s, %s\n", this.status, this.path);
      return false; 
    }

    System.out.format ("[Debug] follow workflow <delete, %s>\n", path);

    this.status = BlobStatus.Deleted;
    this.lastUpdate = new Date();
    return true; 
  }

  public boolean doDelete (WebService ws)  {

    if (this.status != BlobStatus.Deleted) {
      System.err.format ("[Warn.] Wrong workflow <doDelete> %s, %s\n", this.status, this.path);
      return false; 
    }

    System.out.format ("[Debug] follow workflow <doDelete, %s>\n", path);

    File fp = this.buildFile();
    if (fp.exists ()) {
      if (fp.delete()) {
        ws.sendDelete (this.path, this.hash, this.version);
      }
      return false;
    }

    return true;
  }

  public boolean somethingToDo (Date timer) 
  {
    return ((this.status == BlobStatus.UpdateLocal && 
        this.lastUpdate.compareTo (timer) > 0) || 
        this.status == BlobStatus.InStore ||
        this.status == BlobStatus.UpdateExtern ||
        this.status == BlobStatus.Deleted);
  }

  public boolean doSomething (Date timer, DirectoryMirror dm) 
  {
    BlobStatus st = this.status;
    // System.out.format ("[Trace] Action: %s  [%s - %s]\n", this.status, this.lastUpdate, timer);
    if (this.status == BlobStatus.UpdateLocal && 
        this.lastUpdate.compareTo (timer) > 0) {
      this.save ();
      // System.err.format ("[Debug] Update %s [%s]\n", this.path, this.hash);
      dm.webService.sendUpdate (this.path, this.hash, this.parent, this.version, this.type.toString());
      return true;
    } else if (this.status == BlobStatus.InStore) {
      this.extract ();
      return true;
      // TODO Huge file may crash the apps here
    } else if (this.status == BlobStatus.UpdateExtern) {
      this.reload (dm.getNamespace(), dm.webService);
    } else if (this.status == BlobStatus.Deleted) {
      if (this.doDelete (dm.webService)) {
        dm.dataService.remove(this, this.path);
        return true;
      }
    } else  if (this.status != BlobStatus.Saved && this.status != BlobStatus.Loading) {
      System.out.format ("[Trace] something to do here\n");
    }
    
    System.out.format ("[Trace] Action %s -> %s\n", st, this.status);
    return false;
  }

  public Path getPath () 
  {
    String subdir = this.topDir.toString() + "/.pbox/obj/" + this.hash.substring(0,2) + "/";    
    return Paths.get (subdir + this.hash.substring(2));
  }

  @Override
  public String toString () 
  {
    return "<" this.status + "> " + this.path.toString() + " (" + this.version + ") [" + this.hash;
  }
}

