/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Entpacker fuer Abbilddateien
 */

package jkcemu.disk;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import javax.swing.JOptionPane;
import jkcemu.base.BaseDlg;
import jkcemu.base.DeviceIO;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileEntry;
import jkcemu.file.FileUtil;
import jkcemu.lang.LangUtil;
import jkcemu.text.TextUtil;


public class DiskUtil
{

  public static final String[] anaDiskFileExt   = { ".dump" };
  public static final String[] copyQMFileExt    = { ".cqm", ".qm" };
  public static final String[] dskFileExt       = { ".dsk" };
  public static final String[] imageDiskFileExt = { ".imd" };
  public static final String[] isoFileExt       = { ".iso" };
  public static final String[] teleDiskFileExt  = { ".td0" };
  public static final String[] plainDiskFileExt = {
					".dd", ".img", ".image", ".raw" };

  public static final String[] gzAnaDiskFileExt   = { ".dump.gz" };
  public static final String[] gzCopyQMFileExt    = { ".cqm.gz", ".qm.gz" };
  public static final String[] gzDskFileExt       = { ".dsk.gz" };
  public static final String[] gzImageDiskFileExt = { ".imd.gz" };
  public static final String[] gzISOFileExt       = { ".iso.gz" };
  public static final String[] gzTeleDiskFileExt  = { ".td0.gz" };
  public static final String[] gzPlainDiskFileExt = {
						".dd.gz",
						".img.gz",
						".image.gz",
						".raw.gz" };


  private static FloppyDiskFormat[] stdFloppyFormats35 = {
			new FloppyDiskFormat( 80, 2,  9, 512 ),
			new FloppyDiskFormat( 80, 2, 18, 512 ),
			new FloppyDiskFormat( 80, 2, 36, 512 ) };


  public static boolean checkAndConfirmWarning(
				Component          owner,
				AbstractFloppyDisk disk )
  {
    boolean rv = true;
    if( disk != null ) {
      String msg = disk.getWarningText();
      if( msg != null ) {
	if( JOptionPane.showConfirmDialog(
		EmuUtil.getWindow( owner ),
		msg,
		LangUtil.tr( "Warnung" ),
		JOptionPane.OK_CANCEL_OPTION,
		JOptionPane.WARNING_MESSAGE ) != JOptionPane.OK_OPTION )
	{
	  rv = false;
	}
      }
    }
    return rv;
  }


  public static boolean checkFileExt(
				Component   owner,
				File        file,
				String[]... extensions )
  {
    boolean rv = false;
    String  s  = file.getName();
    if( s != null ) {
      s = s.toLowerCase();
      for( int i = 0; i < extensions.length; i++ ) {
	if( TextUtil.endsWith( s, extensions[ i ] ) ) {
	  rv = true;
	  break;
	}
      }
    }
    if( !rv ) {
      rv = BaseDlg.showYesNoWarningDlg(
		owner,
		"Die Dateiendung entspricht nicht der f\u00FCr"
			+ " diesen Dateityp \u00FCblichen Endung.\n"
			+ "Wenn Sie die Datei sp\u00E4ter einmal"
			+ " \u00F6ffnen m\u00F6chten,\n"
			+ "wird JKCEMU den Dateityp nicht richtig"
			+ " erkennen k\u00F6nnen.\n\n"
			+ "M\u00F6chten Sie trotzdem fortsetzen?",
		"Achtung" );
    }
    return rv;
  }


  public static FloppyDiskFormat getFloppyDiskFormat(
				DeviceIO.RandomAccessDevice rad )
						throws IOException
  {
    FloppyDiskFormat fmt = null;
    if( rad != null ) {
      long              diskSize = 0;
      DeviceIO.DiskInfo diskInfo = rad.getDiskInfo();
      if( diskInfo != null ) {
	if( diskInfo.hasGeometry() ) {
	  int cyls            = diskInfo.getCylinders();
	  int heads           = diskInfo.getHeads();
	  int sectorsPerTrack = diskInfo.getSectorsPerTrack();
	  int sectorSize      = diskInfo.getSectorSize();
	  if( (heads > 2)
	      || (cyls > 0x7F)
	      || (sectorsPerTrack > 0x7F)
	      || (sectorSize > 0x2000) )
	  {
	    throw new IOException( "Datentr\u00E4ger ist keine Diskette." );
	  }
	  fmt = new FloppyDiskFormat(
				cyls,
				heads,
				sectorsPerTrack,
				sectorSize );
	}
	diskSize = diskInfo.getDiskSize();
      }
      if( fmt == null ) {
	for( FloppyDiskFormat tmpFmt : stdFloppyFormats35 ) {
	  if( diskSize == tmpFmt.getDiskSize() ) {
	    fmt = tmpFmt;
	    break;
	  }
	}
      }
      if( fmt == null ) {
	for( FloppyDiskFormat tmpFmt : stdFloppyFormats35 ) {
	  if( DiskUtil.equalsDiskSize( rad, tmpFmt.getDiskSize() ) ) {
	    fmt = tmpFmt;
	    break;
	  }
	}
      }
    }
    if( fmt == null ) {
      throw new IOException( "Diskettenformat unbekannt"
				+ " oder nicht unterst\u00FCtzt" );
    }
    return fmt;
  }


  /*
   * Lesen einer Diskettenabbilddatei
   * Rueckgabe:
   *   null: Bei Auswahl des Formats Abbrechen gedrueckt
   */
  public static AbstractFloppyDisk readDiskFile(
				Frame   owner,
				File    file,
				boolean enableAutoRepair ) throws IOException
  {
    AbstractFloppyDisk disk = readNonPlainDiskFile(
						owner,
						file,
						enableAutoRepair );
    if( (disk == null) && (file != null) ) {
      String fName = file.getName();
      if( fName != null ) {
	fName = fName.toLowerCase();
	if( TextUtil.endsWith( fName, DiskUtil.plainDiskFileExt )
	    || TextUtil.endsWith( fName, DiskUtil.gzPlainDiskFileExt ) )
	{
	  FloppyDiskFormatDlg dlg = new FloppyDiskFormatDlg(
			owner,
			true,
			FloppyDiskFormat.getFormatByDiskSize( file.length() ),
			FloppyDiskFormatDlg.Flag.PHYS_FORMAT );
	  dlg.setVisible( true );
	  FloppyDiskFormat fmt = dlg.getFormat();
	  if( fmt != null ) {
	    disk = PlainDisk.createForByteArray(
			owner,
			file.getPath(),
			FileUtil.readFile(
					file,
					true,
					FloppyDiskFormat.getMaxDiskSize() ),
			fmt );
	  }
	} else {
	  throw new IOException(
			"Unbekanntes Format einer Diskettenabbilddatei" );
	}
      }
    }
    return disk;
  }


  public static AbstractFloppyDisk readNonPlainDiskFile(
				Frame   owner,
				File    file,
				boolean enableAutoRepair ) throws IOException
  {
    AbstractFloppyDisk disk = null;
    if( file != null ) {
      String fName = file.getName();
      if( fName != null ) {
	fName = fName.toLowerCase();
	if( TextUtil.endsWith( fName, DiskUtil.anaDiskFileExt )
	    || TextUtil.endsWith( fName, DiskUtil.gzAnaDiskFileExt ) )
	{
	  disk = AnaDisk.readFile( owner, file );
	}
      }
      if( disk == null ) {
	byte[] header = FileUtil.readFile( file, true, 0x100 );
	if( header != null ) {
	  if( CopyQMDisk.isCopyQMFileHeader( header ) ) {
	    disk = CopyQMDisk.readFile( owner, file );
	  }
	  else if( CPCDisk.isCPCDiskFileHeader( header ) ) {
	    disk = CPCDisk.readFile( owner, file );
	  }
	  else if( ImageDisk.isImageDiskFileHeader( header ) ) {
	    disk = ImageDisk.readFile( owner, file );
	  }
	  else if( TeleDisk.isTeleDiskFileHeader( header ) ) {
	    disk = TeleDisk.readFile( owner, file, enableAutoRepair );
	  }
	}
      }
    }
    return disk;
  }


	/* --- private Methoden --- */

  private static boolean equalsDiskSize(
				DeviceIO.RandomAccessDevice rad,
				int                         diskSize )
  {
    boolean rv = false;
    try {
      /*
       * Bei Zugriff auf ein Laufwerk unter Windows
       * muss der Puffer mit der Sektorgroesse uebereinstimmen.
       * Da die Diskettenformate, die hier getestet werden,
       * entweder 512 oder 1024 Bytes grosse Sektoren haben,
       * wird ein 1024 Byte grosser Puffer verwendet.
       */
      byte[] buf = new byte[ 1024 ];
      if( diskSize >= buf.length ) {
	rad.seek( diskSize - buf.length );
	if( rad.read( buf, 0, buf.length ) == buf.length ) {
	  try {
	    if( rad.read( buf, 0, buf.length ) <= 0 ) {
	      rv = true;
	    }
	  }
	  catch( IOException ex ) {
	    rv = true;
	  }
	}
      }
    }
    catch( IOException ex ) {}
    return rv;
  }


	/* --- Konstruktor --- */

  private DiskUtil()
  {
    // nicht instanziierbar
  }
}
