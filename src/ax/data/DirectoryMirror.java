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

import ax.util.ProgOptions;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.NoSuchAlgorithmException;


public class DirectoryMirror
{  
  private Path path;

  public final DataService dataService;
  public final WatchService watchService;
  public final WebService webService;

  public DirectoryMirror (Path path) 
      throws IOException
  {
    this.path = path;
    // TODO LoadOldStatus

    this.dataService = new DataService(this);
    this.watchService = new WatchService(this);
    this.webService = new WebService(this);

    // TODO What about non checked ! (meaning they have been deleted from local)

  }

  public Path getTopDir() 
  {
    return this.path;
  }

/*
  public void sendToMirror (FileEntry fp) 
  {
    if (this.mirror == null)
      return;

    String name = "/.pbox/obj/" + fp.hash.substring(0, 2) + "/" + fp.hash.substring(2);
    File fsrc = new File (this.path.toString() + name);
    File fdest = new File (this.mirror.path.toString() + name);
    try {
      fdest.getParentFile().mkdirs();
      Files.copy(fsrc.toPath(), fdest.toPath());
    } catch (Exception e) {
    }
    this.mirror.receive (fp.path, fp.parent, fp.hash, fp.type); // TYPE
    
  }

  public void receive (Path path, String parent, String hash, BlobType type) 
  {
    // System.out.format ("New Node %s\n", path);

    FileEntry en = entries.get (path);
    if (en == null) {

      FileEntry e = new FileEntry(path, hash, type, this.path);
      e.extern (hash, parent);
      this.entries.put (path, e);
      System.out.format("[Trace] extern on db %s -> %s\n", hash, path);
      e.extract ();

    } else {
      System.out.format("[Debug] Update not implemented ! -> %s\n", path);
    }

  }
*/

  public void start () {
    new Thread(this.webService).start();
    new Thread(this.watchService).start();
    new Thread(this.dataService).start();
  }


  public static void main(String[] args) throws IOException 
  {
    DirectoryMirror dm = null;
    ProgOptions po = new ProgOptions ("pbox", "personnal/private cloud box", "");
    po.addOption ('d', "directory", "TOPDIR", "The directory to watch");
    po.addOption ('r', "remote", "REMOTE", "Use this endpoint as a remote server");

    po.addOption ('p', "port", "PORT", "Change the port");
    po.addOption ('s', "server", "SERVER", "Open a server");
    po.addOption ('b', "bare", null, "Just caching, data are not extracted");
    po.addOption ('n', "no-version", null, "Try to clean previous version to spare space");
    po.addUsage ("[options] [end-point]...");

    System.out.println ("  pbox  Copyright (C) 2014  AxFab.net");
    System.out.println ("  This program comes with ABSOLUTELY NO WARRANTY.");
    System.out.println ("  This is free software, and you are welcome to redistribute it");
    System.out.println ("  under certain conditions. For more information visit axfab.net.");
    System.out.println ();

    po.parse (args);

    Path dir = Paths.get (po.getValue ('d'));

    try {
      dm = new DirectoryMirror (dir);
    } catch (IOException e) {
      System.err.format ("[Error] Unable to watch over directory: %s/n", dir);
      return;
    }

    if (po.getOption ('p')) {
      dm.webService.setPort (Integer.parseInt(po.getValue('p')));
    }

    if (po.getOption ('r')) {
      dm.webService.addRemote (po.getValue('r'));
    }

    dm.start ();

  }
    /*
    Path dir1 = Paths.get("C:/Dropbox/develop/pbox");
    // Path dir2 = Paths.get("C:/pbox");

    try {
      // DirectoryMirror dm2 = new DirectoryMirror (dir2);
      DirectoryMirror dm1 = new DirectoryMirror (dir1);
      dm1.start();

    } catch (IOException e) {

    }

/ *
    // register directory and process its events
    Path dir = Paths.get(args[dirArg]);
    DirectoryMirror dm = new DirectoryMirror(dir);
    (new Thread(dm)).start();
    FileWatcher watchService = new FileWatcher(dir, dm);
    watchService.processEvents(); */

}


