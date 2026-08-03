/*
 * (c) 2024-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Verwaltung der zuletzt verwendeten Verzeichnisse
 */

package jkcemu.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Properties;
import jkcemu.Main;
import jkcemu.base.EmuUtil;


public class RecentDirsMngr
{
  public static final String FILE_CAT_AUDIO       = "audio";
  public static final String FILE_CAT_DEBUG_BREAK = "debug.breakpoints";
  public static final String FILE_CAT_DEBUG_TRACE = "debug.log";
  public static final String FILE_CAT_DISK        = "disk";
  public static final String FILE_CAT_DU_IN       = "diskunpacker.in";
  public static final String FILE_CAT_DU_OUT      = "diskunpacker.out";
  public static final String FILE_CAT_DV_DISK     = "diskviewer.disk";
  public static final String FILE_CAT_DV_SECTOR   = "diskviewer.sector";
  public static final String FILE_CAT_FC_IN       = "fileconverter.in";
  public static final String FILE_CAT_FC_OUT      = "fileconverter.out";
  public static final String FILE_CAT_FIND        = "find";
  public static final String FILE_CAT_HEXDIFF     = "hexdiff";
  public static final String FILE_CAT_HEXEDIT     = "hexedit";
  public static final String FILE_CAT_HD          = "harddisk";
  public static final String FILE_CAT_IMAGE       = "image";
  public static final String FILE_CAT_LABEL       = "label";
  public static final String FILE_CAT_LOG         = "log";
  public static final String FILE_CAT_PRINT       = "print";
  public static final String FILE_CAT_PROFILE     = "profile";
  public static final String FILE_CAT_PROJECT     = "project";
  public static final String FILE_CAT_RF          = "ramfloppy";
  public static final String FILE_CAT_ROM         = "rom";
  public static final String FILE_CAT_SCREEN      = "screen";
  public static final String FILE_CAT_SECTOR      = "sector";
  public static final String FILE_CAT_SOFTWARE    = "software";
  public static final String FILE_CAT_TEXT        = "text";
  public static final String FILE_CAT_USB         = "usb";
  public static final String RECENT_DIRS_FILE     = "recent_dirs.xml";

  private static Properties properties     = new Properties();
  private static File       recentDirsFile = null;
  private static long       fileMillis     = 0L;


  public static File getRecentDir( String category )
  {
    File       recentDir = null;
    Properties props     = properties;
    File       file      = getRecentDirsFile();
    if( file != null ) {
      // neu laden notwendig?
      long millis = file.lastModified();
      if( (millis < 0)
	  || (fileMillis < 0)
	  || (fileMillis < millis) )
      {
	fileMillis = millis;
	InputStream in = null;
	try {
	  in    = new FileInputStream( recentDirsFile );
	  props = new Properties();
	  props.loadFromXML( in );
	  properties = props;
	}
	catch( IOException ex ) {}
	finally {
	  EmuUtil.closeSilently( in );
	}
      }
    }
    String s = props.getProperty( category );
    if( s != null ) {
      if( !s.isEmpty() ) {
	recentDir = new File( s );
      }
    }
    return recentDir;
  }


  public static void setRecentDir( File dirFile, String category )
  {
    if( dirFile != null ) {
      if( !dirFile.isDirectory() ) {
	dirFile = dirFile.getParentFile();
      }
      if( dirFile != null ) {
	String path = dirFile.getPath();
	if( path != null ) {
	  if( !path.isEmpty() ) {

	    // Datei oeffnen, lesen und speichern
	    boolean    done  = false;
	    Properties props = properties;
	    File       file  = getRecentDirsFile();
	    if( file != null ) {
	      RandomAccessFile raf = null;
	      try {
		raf = new RandomAccessFile( file, "rw" );

		// Datei lesen
		try {
		  props = new Properties();
		  props.loadFromXML(
				new BufferedInputStream(
					new RAFInputStream( raf, true ) ) );
		  properties = props;
		  fileMillis = System.currentTimeMillis();
		}
		catch( Exception ex ) {}

		// Wert setzen
		properties.setProperty( category, path );
		done = true;

		// Datei schreiben
	        raf.seek( 0 );
		raf.setLength( 0 );
		OutputStream out = new BufferedOutputStream(
					new RAFOutputStream( raf, true ) );
		properties.storeToXML(
			out,
			Main.APPNAME + " zuletzt verwendete Verzeichnisse" );
		out.flush();
		raf.close();
		raf = null;
	      }
	      catch( IOException ex ) {}
	      finally {
		EmuUtil.closeSilently( raf );
	      }
	    }
	    if( !done ) {
	      properties.setProperty( category, path );
	    }
	  }
	}
      }
    }
  }


	/* --- private Methoden --- */

  private static File getRecentDirsFile()
  {
    if( recentDirsFile == null ) {
      File configDir = Main.getConfigDir();
      if( configDir != null ) {
	recentDirsFile = new File( configDir, RECENT_DIRS_FILE );
      }
    }
    return recentDirsFile;
  }


	/* --- Konstruktor --- */

  private RecentDirsMngr()
  {
    // nicht instanziierbar
  }
}
