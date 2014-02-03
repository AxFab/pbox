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
package ax.util;

import java.util.*;

public class ProgOptions 
{
  public static final String BOLD = ""; // "\e [1m"
  public static final String REGULAR = ""; // "\e [m"

  class Options {
    public final char shortOpt;
    public final String longOpt;
    public final String field;
    public final String details;
    public boolean actived;
    public String value;

    public Options (char shortOpt, String longOpt, String field, String details)
    {
      this.shortOpt = shortOpt;
      this.longOpt = longOpt;
      this.field = field;
      this.details = details;
      this.actived = false;
      this.value = null;
    }

    public void help () 
    {
      if (this.shortOpt != '\0' && this.longOpt != null) {
        System.out.format ("    %s-%c, --%s%s\n        %s\n\n",
            ProgOptions.BOLD, shortOpt, longOpt, ProgOptions.REGULAR, details); // SPLIT

      } else if (this.shortOpt != '\0') {
        System.out.format ("    %s-%c%s\n        %s\n\n",
            ProgOptions.BOLD, shortOpt, ProgOptions.REGULAR, details); // SPLIT

      } else if (this.longOpt != null) {
        System.out.format ("    %s--%s%s\n        %s\n\n",
            ProgOptions.BOLD, longOpt, ProgOptions.REGULAR, details); // SPLIT
      }
    }
  }

  private String name;
  private String brief;
  private String summary;
  private List<Options> options;
  private List<String> dfltArgs;
  private List<String> usages;


  public ProgOptions (String name, String brief, String summary)
  {
    this.name = name;
    this.brief = brief;
    this.summary = summary;
    this.options = new LinkedList<Options> ();
    this.dfltArgs = new LinkedList<String> ();
    this.usages = new LinkedList<String> ();
  }

  public void addOption (char shortOpt, String longOpt, String field, String details) 
  {
    Options opt = new Options (shortOpt, longOpt, field, details);
    options.add (opt);
  }

  public void addUsage (String usage) 
  {
    usages.add (usage);
  }

  public void help () 
  {
    System.out.format ("\n%sNAME%s\n", BOLD, REGULAR);
    System.out.format ("    %s - %s\n", this.name, this.brief);
    System.out.format ("\n%sSYNOPSIS%s\n", BOLD, REGULAR);
    for (String us : usages) {
      System.out.format ("    %s %s\n", this.name, us);
    }

    System.out.format ("\n%sDESCRIPTION%s\n", BOLD, REGULAR); 
    System.out.format ("    %s\n\n", this.summary); // SPLIT

    for (Options opt : options) {
      opt.help ();
    }

    System.out.format ("    %s--%s%s\n        %s\n\n",
        BOLD, "help", REGULAR, "display this help and exit"); 
    System.out.format ("    %s--%s%s\n        %s\n\n",
        BOLD, "version", REGULAR, "output version information and exit"); 

  }

  public Options getShortOpt (char shortOpt)
  {
    for (Options opt : options) {
      if (opt.shortOpt == shortOpt) {
        return opt;
      }
    }
 
    return null;
  }

  public Options getLongOpt (String longOpt)
  {
    for (Options opt : options) {
      if (opt.longOpt.equals(longOpt)) {
        return opt;
      }
    }
 
    return null;
  }

  public String getValue (char opt) 
  {
    Options s = getShortOpt (opt);
    if (s == null)
      return null;
    return s.value;
  }

  public boolean getOption (char opt) 
  {
    Options s = getShortOpt (opt);
    if (s == null)
      return false;
    return s.actived;
  }

  public void parse (String[] args) 
  {
    for (int i=0; i < args.length; i++) {

      if (args[i].charAt(0) == '-') {

        if (args[i].charAt(1) == '-') {
          String argstring = args[i].substring(2);
          if (argstring.equals  ("help")) 
          {
            help();
            System.exit(0);
          }
          Options opt = getLongOpt (argstring);
          if (opt != null) {
            opt.actived = true;
            if (opt.field != null) {
              opt.value = args[++i];
            }
          } else {
            System.err.format ("Command --%s ignored: Unknown\n", argstring);
          }
        } else {
          for (int j=1; j<args[i].length(); ++j) {
            if (args[i].charAt(j) == 'h') 
            {
              help();
              System.exit(0);
            }
            Options opt = getShortOpt (args[i].charAt(j));
            if (opt != null) {
              opt.actived = true;
              if (opt.field != null) {
                opt.value = args[++i];
                break;
              }
            } else {
              System.err.format ("Command -%c ignored: Unknown\n", args[i].charAt(j));
            }
          }
        }       
      } else {
        dfltArgs.add(args[i]);
      }     
    } 
  }
  
}

