/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation einer Diskette basierend auf einer strukturlosen Abbilddatei
 */

package jkcemu.disk;

import java.awt.Frame;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import jkcemu.base.DeviceIO;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileUtil;
import jkcemu.lang.LangUtil;


public class PlainDisk extends RegularFormatFloppyDisk
{
  public static final String PROP_DRIVE = "drive";

  private String                      fileName;
  private FileLock                    fileLock;
  private DeviceIO.RandomAccessDevice rad;
  private RandomAccessFile            raf;
  private byte[]                      diskBytes;
  private boolean                     readOnly;
  private boolean                     appendable;
  private int                         sectorSizeCode;


  public static PlainDisk createForDrive(
				Frame                       owner,
				String                      driveFileName,
				DeviceIO.RandomAccessDevice rad,
				boolean                     readOnly,
				FloppyDiskFormat            fmt )
  {
    return new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			driveFileName,
			rad,
			null,
			null,
			null,
			readOnly,
			false );
  }


  public static PlainDisk createForByteArray(
			Frame            owner,
			String           fileName,
			byte[]           fileBytes,
			FloppyDiskFormat fmt,
			int              interleave ) throws IOException
  {
    PlainDisk rv = null;
    if( fileBytes != null ) {
      rv = new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			interleave,
			fileName,
			null,
			null,
			null,
			fileBytes,
			true,
			false );
    }
    return rv;
  }


  public static PlainDisk createForByteArray(
			Frame            owner,
			String           fileName,
			byte[]           fileBytes,
			FloppyDiskFormat fmt ) throws IOException
  {
    return createForByteArray( owner, fileName, fileBytes, fmt, 0 );
  }


  public static PlainDisk createForFile(
				Frame            owner,
				String           driveFileName,
				RandomAccessFile raf,
				boolean          readOnly,
				FloppyDiskFormat fmt )
  {
    return new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			driveFileName,
			null,
			raf,
			null,
			null,
			readOnly,
			false );
  }


  public static String export(
			AbstractFloppyDisk disk,
			File               file ) throws IOException
  {
    StringBuilder msgBuf  = new StringBuilder();
    IOException   ioEx    = null;
    OutputStream  out     = null;
    boolean       created = false;
    try {
      out     = new BufferedOutputStream( new FileOutputStream( file ) );
      created = true;
      writeDiskAsPlainData( disk, out, msgBuf, null, null, null );
      out.close();
      out = null;

      if( msgBuf.length() > 0 ) {
	msgBuf.append( "\nDie angezeigten Informationen k\u00F6nnen"
		+ " in einer einfachen Abbilddatei nicht gespeichert werden\n"
		+ "und sind deshalb in der erzeugten Datei"
		+ " nicht mehr enthalten.\n" );
      }
    }
    catch( IOException ex ) {
      ioEx = ex;
    }
    finally {
      EmuUtil.closeSilently( out );
    }
    if( ioEx != null ) {
      if( created ) {
	file.delete();
      }
      throw ioEx;
    }
    return msgBuf.length() > 0 ? msgBuf.toString() : null;
  }


  public static PlainDisk newFile( Frame owner, File file ) throws IOException
  {
    PlainDisk        rv  = null;
    FileLock         fl  = null;
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile( file, "rw" );
      fl  = FileUtil.lockFile( file, raf );
      raf.seek( 0 );
      raf.setLength( 0 );
      rv = new PlainDisk(
			owner,
			0,
			0,
			0,
			0,
			0,			// kein Interleave
			file.getPath(),
			null,
			raf,
			fl,
			null,
			false,
			true );
    }
    finally {
      if( rv == null ) {
        FileUtil.releaseSilently( fl );
        EmuUtil.closeSilently( raf );
      }
    }
    return rv;
  }


  public static PlainDisk openFile(
				Frame            owner,
				File             file,
				boolean          readOnly,
				FloppyDiskFormat fmt ) throws IOException
  {
    PlainDisk        rv  = null;
    FileLock         fl  = null;
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile( file, readOnly ? "r" : "rw" );
      if( !readOnly ) {
	fl = FileUtil.lockFile( file, raf );
      }
      rv = new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			file.getPath(),
			null,
			raf,
			fl,
			null,
			readOnly,
			!readOnly );
    }
    finally {
      if( rv == null ) {
        FileUtil.releaseSilently( fl );
        EmuUtil.closeSilently( raf );
      }
    }
    return rv;
  }


  public static void writeDiskAsPlainData(
				AbstractFloppyDisk disk,
				OutputStream       out,
				StringBuilder      msgBuf,
				AtomicInteger      rvSectorsPerTrack,
				AtomicInteger      rvSectorSize,
				AtomicInteger      rvSectorOffset )
							throws IOException
  {
    // Format ermitteln
    SortedSet<SectorData> trackSectors = disk.getSortedTrackSectors( 0, 0 );
    int sectorsPerTrack = trackSectors.size();
    if( sectorsPerTrack <= 0 ) {
      throwEmptyFirstTrack();
    }
    int sectorOffset   = 0;
    int sectorSizeCode = 0;
    try {
      SectorData firstSector = trackSectors.first();
      sectorSizeCode = firstSector.getSizeCode();
      sectorOffset   = firstSector.getSectorNum() - 1;
    }
    catch( NoSuchElementException ex ) {
      throwEmptyFirstTrack();
    }
    int sectorSize = SectorData.getSizeBySizeCode( sectorSizeCode );

    // Datenbereich schreiben
    int cyls  = disk.getCylinders();
    int sides = disk.getSides();
    for( int cyl = 0; cyl < cyls; cyl++ ) {
      for( int head = 0; head < sides; head++ ) {
	int sectorNum = sectorOffset + 1;
	trackSectors  = disk.getSortedTrackSectors( cyl, head );
	for( SectorData sector : trackSectors ) {
	  if( (sector.getCylinder() != cyl)
	      || (sector.getHead() != head)
	      || (sector.getSectorNum() != sectorNum)
	      || (sector.getSizeCode() != sectorSizeCode) )
	  {
	    throw new IOException(
		String.format(
			"Spur %d, Seite %d, Sektor %d: Sektor"
				+ " [%d,%d,%d,%d] anstelle [%d,%d,%d,%d]"
				+ " gelesen",
			cyl,
			head + 1,
			sectorNum,
			sector.getCylinder(),
			sector.getHead(),
			sector.getSectorNum(),
			sector.getSizeCode(),
			cyl,
			head,
			sectorNum,
			sectorSizeCode ) );
	  }
	  if( (msgBuf != null)
	      && (sector.checkError()
		  || sector.hasBogusID()
		  || sector.getDataDeleted()) )
	  {
	    msgBuf.append(
			String.format(
				"Spur %d, Seite %d, Sektor %d:",
				cyl,
				head + 1,
				sector.getSectorNum() ) );
	    boolean appended = false;
	    if( sector.hasBogusID() ) {
	      msgBuf.append( " Sektor-ID generiert" );
	      appended = true;
	    }
	    if( sector.checkError() ) {
	      if( appended ) {
		msgBuf.append( ',' );
	      }
	      msgBuf.append( " CRC-Fehler" );
	      appended = true;
	    }
	    if( sector.getDataDeleted() ) {
	      if( appended ) {
		msgBuf.append( ',' );
	      }
	      msgBuf.append( " Deleted Data Address Mark" );
	    }
	    msgBuf.append( '\n' );
	  }
	  if( sector.getDataLength() > sectorSize ) {
	    throw new IOException(
		String.format(
			"Seite %d, Spur %d: Sektor %d zu gro\u00DF",
			head + 1,
			cyl,
			sector.getSectorNum() ) );
	  }
	  int n = sector.writeTo( out, sectorSize );
	  while( n < sectorSize ) {
	    out.write( 0 );
	    n++;
	  }
	  sectorNum++;
	}
      }
    }
    if( rvSectorsPerTrack != null ) {
      rvSectorsPerTrack.set( sectorsPerTrack );
    }
    if( rvSectorSize != null ) {
      rvSectorSize.set( sectorSize );
    }
    if( rvSectorOffset != null ) {
      rvSectorOffset.set( sectorOffset );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public synchronized void closeSilently()
  {
    FileUtil.releaseSilently( this.fileLock );
    EmuUtil.closeSilently( this.raf );
    EmuUtil.closeSilently( this.rad );
  }


  @Override
  public boolean formatTrack(
			int        physCyl,
			int        physHead,
			SectorID[] sectorIDs,
			byte[]     dataBuf,
			boolean    mfmMode )
  {
    boolean rv = false;
    if( !this.readOnly
	&& ((this.rad != null) || (this.raf != null))
	&& (sectorIDs != null)
	&& (dataBuf != null) )
    {
      int oldSectorSize = getSectorSize();
      if( (sectorIDs.length > 0)
	  && ((oldSectorSize == 0) || (oldSectorSize == dataBuf.length))
	  && this.appendable )
      {
	rv = true;
	try {

	  /*
	   * Sektoren auf die Einschraenkungen
	   * einer einfachen Abbilddatei pruefen
	   */
	  boolean             regular   = true;
	  int                 sizeCode  = -1;
	  SortedSet<SectorID> sortedIDs = new TreeSet<>();
	  for( SectorID sectorID : sectorIDs ) {
	    if( sizeCode < 0 ) {
	      sizeCode = sectorID.getSizeCode();
	    }
	    if( (sectorID.getCylinder() != physCyl)
		|| (sectorID.getHead() != physHead)
		|| (sectorID.getSizeCode() != sizeCode) )
	    {
	      regular = false;
	      break;
	    }
	    sortedIDs.add( sectorID );
	  }
	  if( !sortedIDs.isEmpty() ) {
	    if( (sortedIDs.first().getSectorNum() != 1)
		|| (sortedIDs.last().getSectorNum() != sectorIDs.length) )
	    {
	      regular = false;
	    }
	  }
	  if( !regular ) {
	    throw new IOException(
		LangUtil.getText( "disk.error.formatting_irregular" ) );
	  }

	  // eigentliches Formatieren
	  for( int i = 0; i < sectorIDs.length; i++ ) {
	    int  sectorIdx = sectorIDs[ i ].getSectorNum() - 1;
	    long filePos   = calcFilePos( physCyl, physHead, sectorIdx );
	    if( filePos >= 0 ) {
	      int sectorsPerTrack = getSectorsPerTrack();
	      int sectorSize      = getSectorSize();
	      if( ((sectorsPerTrack > 0)
			&& (sectorsPerTrack != sectorIDs.length))
		  || ((sectorSize > 0)
			&& (sectorSize != dataBuf.length)) )
	      {
		throw new IOException(
			LangUtil.getText( "disk.error.formatting_track" ) );
	      }
	      setSectorsPerTrack( sectorIDs.length );
	      setSectorSize( dataBuf.length );
	      this.sectorSizeCode = sectorIDs[ 0 ].getSizeCode();
	      if( this.rad != null ) {
		this.rad.seek( filePos );
		this.rad.write( dataBuf, 0, dataBuf.length );
	      } else {
		this.raf.seek( filePos );
		this.raf.write( dataBuf );
	      }
	      trackExists( physCyl, physHead );
	    } else {
	      rv = false;
	      break;
	    }
	  }
	  diskFormatChanged();
	}
	catch( IOException ex ) {
	  rv = false;
	  fireShowError( LangUtil.getText(
				"disk.error.appending_sectors_failed" ),
			ex );
	}
      } else {
	rv = super.formatTrack(
			physCyl,
			physHead,
			sectorIDs,
			dataBuf,
			mfmMode );
      }
    }
    return rv;
  }


  @Override
  public String getFileFormatText()
  {
    return "Einfache Abbilddatei";
  }


  @Override
  public synchronized SectorData getSectorByIndex(
					int physCyl,
					int physHead,
					int sectorIdx )
  {
    return getSectorByIndexInternal(
				physCyl,
				physHead,
				sectorIndexToInterleave(
						sectorIdx,
						getSectorsPerTrack() ) );
  }


  @Override
  public SectorData getSectorByID(
				int physCyl,
				int physHead,
				int cyl,
				int head,
				int sectorNum,
				int sizeCode )
  {
    SectorData rv = getSectorByIndexInternal(
					physCyl,
					physHead,
					sectorNum - 1 );
    if( rv != null ) {
      if( (rv.getCylinder() != cyl)
	  || (rv.getHead() != head)
	  || (rv.getSectorNum() != sectorNum)
	  || ((sizeCode >= 0) && (rv.getSizeCode() != sizeCode)) )
      {
	rv = null;
      }
    }
    return rv;
  }


  @Override
  public boolean isReadOnly()
  {
    return this.readOnly;
  }


  @Override
  public void putSettingsTo( Properties props, String prefix )
  {
    super.putSettingsTo( props, prefix );
    if( (props != null) && (fileName != null) ) {
      if( this.rad != null ) {
	props.setProperty( prefix + PROP_DRIVE, this.fileName );
      } else {
	props.setProperty( prefix + PROP_FILE, this.fileName );
      }
    }
  }


  @Override
  public boolean writeSector(
			int        physCyl,
			int        physHead,
			SectorData sector,
			byte[]     dataBuf,
			int        dataLen,
			boolean    dataDeleted )
  {
    boolean rv = false;
    if( !this.readOnly
	&& ((this.rad != null) || (this.raf != null))
	&& (sector != null)
	&& (dataBuf != null)
	&& !dataDeleted )
    {
      if( (sector.getDisk() == this) && (dataLen == getSectorSize()) ) {
	int  sectorIdx = sector.getIndexOnCylinder();
	long filePos   = calcFilePos( physCyl, physHead, sectorIdx );
	if( filePos == sector.getFilePos() ) {
	  try {
	    if( this.rad != null ) {
	      this.rad.seek( filePos );
	      this.rad.write( dataBuf, 0, dataLen );
	    } else {
	      this.raf.seek( filePos );
	      this.raf.write( dataBuf, 0, dataLen );
	    }
	    rv = true;
	  }
	  catch( IOException ex ) {
	    fireShowWriteError(
			physCyl,
			physHead,
			sector.getSectorNum(),
			ex );
	    sector.setError( true );
	  }
	}
      }
    }
    return rv;
  }


	/* --- private Methoden --- */

  private PlainDisk(
		Frame                       owner,
		int                         cyls,
		int                         sides,
		int                         sectorsPerTrack,
		int                         sectorSize,
		int                         interleave,
		String                      fileName,
		DeviceIO.RandomAccessDevice rad,
		RandomAccessFile            raf,
		FileLock                    fileLock,
		byte[]                      diskBytes,
		boolean                     readOnly,
		boolean                     appendable )
  {
    super( owner, cyls, sides, sectorsPerTrack, sectorSize, interleave );
    this.fileName       = fileName;
    this.rad            = rad;
    this.raf            = raf;
    this.fileLock       = fileLock;
    this.diskBytes      = diskBytes;
    this.readOnly       = readOnly;
    this.appendable     = appendable;
    this.sectorSizeCode = SectorData.getSizeCodeBySize( sectorSize );
  }


  private long calcFilePos( int cyl, int head, int sectorIdx )
  {
    head &= 0x01;

    long rv         = -1;
    int  sectorSize = getSectorSize();
    if( (cyl >= 0) && (sectorIdx >= 0) ) {
      int cyls            = getCylinders();
      int sides           = getSides();
      int sectorsPerTrack = getSectorsPerTrack();
      if( (head < sides)
	  && (sectorIdx < sectorsPerTrack)
	  && (sectorSize > 0) )
      {
	int nSkipSectors = sides * sectorsPerTrack * cyl;
	if( head > 0 ) {
	  nSkipSectors += sectorsPerTrack;
	}
	nSkipSectors += sectorIdx;
	rv = (long) nSkipSectors * (long) sectorSize;
      } else {
	/*
	 * Waehrend des Formatierens sind die Formatinformationen
	 * noch unvollstaendig.
	 * Deshalb wird hier sichergestellt,
	 * dass die Positionsberechnung funktioniert,
	 * wenn aufsteigend formatiert wird.
	 */
	if( (cyl == 0) && (cyls <= 1) ) {
	  if( (head == 0) && (sectorIdx == 0) ) {
	    rv = 0L;
	  } else {
	    if( sectorSize > 0 ) {
	      if( head == 0 ) {
		rv = sectorIdx * sectorSize;
	      }
	      else if( (head == 1) && (sectorsPerTrack > 0) ) {
		rv = (sectorsPerTrack + sectorIdx) * sectorSize;
	      }
	    }
	  }
	}
      }
    }
    return rv;
  }


  private synchronized SectorData getSectorByIndexInternal(
							int physCyl,
							int physHead,
							int sectorIdx )
  {
    SectorData rv         = null;
    int        sectorSize = getSectorSize();
    long       filePos    = calcFilePos( physCyl, physHead, sectorIdx );
    if( (sectorSize > 0) && (filePos >= 0) ) {
      if( this.diskBytes != null ) {
	if( filePos <= Integer.MAX_VALUE ) {
	  rv = new SectorData(
			sectorIdx,
			physCyl,
			physHead,
			sectorIdx + 1,
			this.sectorSizeCode,
			this.diskBytes,
			(int) filePos,
			sectorSize );
	}
      }
      else if( (this.rad != null) || (this.raf != null) ) {
	try {
	  byte[] buf = new byte[ sectorSize ];
	  int    len = -1;
	  if( this.rad != null ) {
	    this.rad.seek( filePos );
	    len = this.rad.read( buf, 0, buf.length );
	  } else {
	    this.raf.seek( filePos );
	    len = this.raf.read( buf );
	  }
	  if( len > 0 ) {
	    // falls nicht vollstaendig gelesen wurde
	    while( len < buf.length ) {
	      int n = -1;
	      if( this.rad != null ) {
		n = this.rad.read( buf, len, buf.length - len );
	      } else {
		n = this.raf.read( buf, len, buf.length - len );
	      }
	      if( n > 0 ) {
		len += n;
	      } else {
		break;
	      }
	    }
	  }
	  if( len > 0 ) {
	    rv = new SectorData(
				sectorIdx,
				physCyl,
				physHead,
				sectorIdx + 1,
				this.sectorSizeCode,
				buf,
				0,
				len );
	  }
	}
	catch( IOException ex ) {
	  fireShowReadError( physCyl, physHead, sectorIdx + 1, ex );
	  rv = new SectorData(
			sectorIdx,
			physCyl,
			physHead,
			sectorIdx + 1,
			this.sectorSizeCode,
			null,
			0,
			0 );
	  rv.setError( true );
	}
      }
      if( rv != null ) {
	rv.setDisk( this );
	rv.setFilePos( filePos );
	rv.setFilePortionLen( sectorSize );
      }
    }
    return rv;
  }
}
