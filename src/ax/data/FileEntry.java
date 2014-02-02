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
package ax.net;

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
  Unknown, Saved, UpdateLocal, UpdateExtern, InStore, Faulted, Loading
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

  public FileEntry (Path path, Path topDir) 
  {
    this.locked = new AtomicInteger(0);
    this.topDir = topDir;
    this.version = 0;
    File fp = new File (path.toString());
    this.name = fp.getName();
    this.path = path;
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
    this.hash = hash;
    this.parent = pHash;
    this.version = version;
    File fp = new File (path.toString());
    this.name = fp.getName();
    this.path = path;
    this.status = BlobStatus.UpdateExtern;
    this.type = BlobType.valueOf (type);
  }

  public boolean trylock () 
  {
    return this.locked.getAndSet(1) == 0;
  }

  public boolean lock () 
  {
    while (this.locked.getAndSet(1) != 0)
      Thread.yield();
    
    return true;
  }

  public void unlock () 
  {
    this.locked.set (0);
  }

  public void invalidate () 
  {
    if (this.status != BlobStatus.Saved && 
        this.status != BlobStatus.UpdateLocal) {
      System.err.println ("[Warn.] Wrong workflow <invalidate>");
      return; 
    }

    System.out.println ("[Debug] follow workflow <invalidate>");

    this.status = BlobStatus.UpdateLocal;
    this.lastUpdate = new Date();
  }

  public void extern (String hash, String parent, long version) 
  {
    if (this.status != BlobStatus.Saved) {
      System.err.println ("[Warn.] Wrong workflow <extern>");
      return; 
    }

    System.out.println ("[Debug] follow workflow <extern>");

    if (this.hash != parent) {
      System.err.println ("[Warn.] This is a collision");
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
      System.err.println ("[Warn.] Wrong workflow <save>");
      return; 
    }

    System.out.println ("[Debug] follow workflow <save>");

    String hash;
    long lg;

    try {
      File fp = new File (this.path.toString());
      lg = fp.length();

      File fTmp = new File (topDir.toString() + "/.pbox/thread-id.tmp");
      BeamOut bOut = new BeamOut (fTmp);
      bOut.writeOpenObject ("data");
      bOut.writeString ("name", this.path.toString());
      bOut.writeString ("type", this.type.toString());
      bOut.writeString ("parent", this.parent);
      bOut.writeInteger ("version", this.version + 1);

      if (fp.isFile()) {
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

    Store.updated (this.path, this.parent, this.hash, this.type.toString());
    // System.out.format ("[Trace] new record %s for %s\n", this.hash, this.path);
  }

  public void reload (String namespace) 
  {
    if (this.status != BlobStatus.UpdateExtern) {
      System.err.println ("[Warn.] Wrong workflow <reload>");
      return; 
    }

    System.out.println ("[Debug] follow workflow <reload>");

    this.status = BlobStatus.Loading;

    if (this.length < 64L * 1024L) {
      try {
        Store.get (namespace, this.hash);
      } catch (IOException e) {
        this.status = BlobStatus.UpdateExtern;
        System.err.format ("[Error] %s\n", e.getMessage());
        return;
      }
      this.status = BlobStatus.InStore;
    } else if (this.length < 128L * 1024L * 1024L) {
      Store.async (namespace, this.hash, this);
    } else {
      Store.peer (namespace, this.hash, this);
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
      System.err.println ("[Warn.] Wrong workflow <extract>");
      return; 
    }

    System.out.println ("[Debug] follow workflow <extract>");

    try {
      File out = new File (this.topDir + "/" + this.path);
      if (this.type == BlobType.Dir) {
        out.mkdirs();
      } else if (this.type == BlobType.File || this.type == BlobType.Exe) {
        out.getParentFile().mkdirs();
        File in = new File (this.topDir + "/.pbox/obj/" + this.hash.substring(0,2) + "/" + 
          this.hash.substring(2));
        BeamIn bIn = new BeamIn(in);

        String label = bIn.readLabel ();
        bIn.readObject ();
        while ((label = bIn.readLabel ()) != null) {
          switch (label) {
            case "name":
              label = bIn.readString ();
              //System.out.format ("{} name %s\n", label);
              break;
            case "type":
              label = bIn.readString ();
              // System.out.format ("{} type %s\n", kvs.readString ());
              break;
            case "parent":
              label = bIn.readString ();
              //System.out.format ("{} parent %s\n", kvs.readString ());
              break;
            case "version":
              bIn.readInteger ();
              //System.out.format ("{} version %d\n", kvs.readInteger ());
              break;
            case "content":
              System.err.format ("[Trace] Re-write file %s\n", out.getPath());
              bIn.readFile (out);
              break;
          }
        }
      }
    } catch (BeamFormatException e) {
      System.err.format ("[Error] Wrong format on Beam extraction %s\n", e.getMessage());
      return;
    } catch (IOException e) {
      System.err.format ("[Error] IOError on extract %s\n", e.getMessage());
      return;
    }

    this.status = BlobStatus.Saved;
  }

  public boolean somethingToDo (Date timer) 
  {
    return ((this.status == BlobStatus.UpdateLocal && 
        this.lastUpdate.compareTo (timer) > 0) || 
        this.status == BlobStatus.InStore ||
        this.status == BlobStatus.UpdateExtern);
  }

  public boolean doSomething (Date timer, String namespace) 
  {
    // System.out.format ("[Trace] Action: %s  [%s - %s]\n", this.status, this.lastUpdate, timer);
    if (this.status == BlobStatus.UpdateLocal && 
        this.lastUpdate.compareTo (timer) > 0) {
      this.save ();
      return true;
    } else if (this.status == BlobStatus.InStore) {
      this.extract ();
      return true;
      // TODO Huge file may crash the apps here
    } else if (this.status == BlobStatus.UpdateExtern) {
      this.reload (namespace);
    } else  if (this.status != BlobStatus.Saved && this.status != BlobStatus.Loading) {
      System.out.format ("[Trace] something to do here\n");
    }

    return false;
  }

  public Path getPath () 
  {
    String subdir = this.topDir.toString() + "/.pbox/obj/" + this.hash.substring(0,2) + "/";    
    return Paths.get (subdir + this.hash.substring(2));
  }
}

