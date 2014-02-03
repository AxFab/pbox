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


public class Store 
{

  public static void updated (Path path, String hash, String pHash, long version, String type) 
  {
    /*
    System.out.format("[update] %s -> %s\n", hash, path);
    webService.sendUpdate (path, hash, pHash, version, type);
    */
  }
}

