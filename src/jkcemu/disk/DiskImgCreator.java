/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Erzeugen einer Diskettenabbilddatei
 */

package jkcemu.disk;

import java.awt.Frame;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileTimesData;
import jkcemu.lang.LangUtil;


public class DiskImgCreator
{
  public static class Disk extends AbstractFloppyDisk
  {
    private int    sectorsPerTrack;
    private int    sectorSize;
    private int    sysTracks;
    private int    sysSectPerTrack;
    private int    sysSectorSize;
    private int    sysSectorSizeCode;
    private int    sectorSizeCode;
    private byte[] diskBytes;


    private Disk(
		Frame  owner,
		int    cyls,
		int    sides,
		int    sectorsPerTrack,
		int    sectorSize,
		int    interleave,
		int    sysTracks,
		int    sysSectPerTrack,
		int    sysSectorSize,
		byte[] diskBytes )
    {
      super( owner, cyls, sides, interleave );
      this.sectorsPerTrack   = sectorsPerTrack;
      this.sectorSize        = sectorSize;
      this.sysTracks         = sysTracks;
      this.sysSectPerTrack   = sysSectPerTrack;
      this.sysSectorSize     = sysSectorSize;
      this.sysSectorSizeCode = SectorData.getSizeCodeBySize( sysSectorSize );
      this.sectorSizeCode    = SectorData.getSizeCodeBySize( sectorSize );
      this.diskBytes         = diskBytes;
    }


	/* --- ueberschriebene Methoden --- */

    @Override
    public boolean formatTrack(
			int        physCyl,
			int        physHead,
			SectorID[] sectorIDs,
			byte[]     dataBuf,
			boolean    mfmMode )
    {
      return false;
    }


    @Override
    public int getDiskSize()
    {
      return this.diskBytes.length;
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
      int sectPerTrack = this.sectorsPerTrack;
      if( (physCyl >= 0) && (physCyl < this.sysTracks) ) {
	sectPerTrack = this.sysSectPerTrack;
      }
      return getSectorByIndexInternal(
			physCyl,
			physHead,
			sectorIndexToInterleave( sectorIdx, sectPerTrack ) );
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
    public int getTrackSectorCount( int physCyl, int physHead )
    {
      int rv    = 0;
      int sides = getSides();
      if( (physCyl >= 0) && (physHead < sides) && (sides >= 1) ) {
	if( physCyl < this.sysTracks ) {
	  rv = this.sysSectPerTrack;
	} else if( physCyl < getCylinders() ) {
	  rv = this.sectorsPerTrack;
	}
      }
      return rv;
    }


    @Override
    protected int getSysTracks()
    {
      return this.sysTracks;
    }


    @Override
    public void putSettingsTo( Properties props, String prefix )
    {
      // leer
    }


	/* --- private Methoden --- */

    private SectorData getSectorByIndexInternal(
					int physCyl,
					int physHead,
					int sectorIdx )
    {
      SectorData rv = null;
      int        sides           = getSides();
      if( (physCyl >= 0) && (physCyl < getCylinders())
	  && (physHead >= 0) && (physHead < sides) )
      {
	int sysTrackSize = this.sysSectPerTrack * this.sysSectorSize;
	if( physCyl < this.sysTracks ) {
	  if( (sectorIdx >= 0) && (sectorIdx < this.sysSectPerTrack) ) {
	    int pos = (physCyl * sysTrackSize * sides)
			+ (physHead * sysTrackSize)
			+ (sectorIdx * this.sysSectorSize);
	    if( (pos >= 0)
		&& ((pos + this.sysSectorSize) <= this.diskBytes.length) )
	    {
	      rv = new SectorData(
				sectorIdx,
				physCyl,
				physHead,
				sectorIdx + 1,
				this.sysSectorSizeCode,
				this.diskBytes,
				pos,
				this.sysSectorSize );
	    }
	  }
	} else {
	  if( (sectorIdx >= 0) && (sectorIdx < this.sectorsPerTrack) ) {
	    int trackSize = this.sectorsPerTrack * this.sectorSize;
	    int pos       = (this.sysTracks * sysTrackSize * sides)
				+ ((physCyl - this.sysTracks)
						* trackSize * sides)
				+ (physHead * trackSize)
				+ (sectorIdx * this.sectorSize);
	    if( (pos >= 0)
		&& ((pos + this.sectorSize) <= this.diskBytes.length) )
	    {
	      rv = new SectorData(
				sectorIdx,
				physCyl,
				physHead,
				sectorIdx + 1,
				this.sectorSizeCode,
				this.diskBytes,
				pos,
				this.sectorSize );
	    }
	  }
	}
      }
      return rv;
    }
  };


  private int      cyls;
  private int      sides;
  private int      sectPerTrack;
  private int      sectorSize;
  private int      interleave;
  private int      sysTracks;
  private int      sysSectPerTrack;
  private int      sysSectorSize;
  private int      sysSectorSizeCode;
  private int      sectorSizeCode;
  private int      blockSize;
  private int      dirBlocks;
  private int      begDirArea;
  private int      begFileArea;
  private int      dstDirPos;
  private int      dstFilePos;
  private int      blockNum;
  private int      begTimeFile;
  private int      dstTimeFile;
  private int      endTimeFile;
  private boolean  blockNum16Bit;
  private Calendar calendar;
  private byte[]   diskBuf;


  public DiskImgCreator(
		int     cyls,
		int     sides,
		int     sectPerTrack,
		int     sectorSize,
		int     interleave,
		int     sysTracks,
		int     sysSectPerTrack,
		int     sysSectorSize,
		boolean blockNum16Bit,
		int     blockSize,
		int     dirBlocks,
		boolean dateStamper ) throws IOException
  {
    this.cyls            = cyls;
    this.sides           = sides;
    this.sectPerTrack    = sectPerTrack;
    this.sectorSize      = sectorSize;
    this.interleave      = interleave;
    this.sysTracks       = sysTracks;
    this.sysSectPerTrack = sysSectPerTrack;
    this.sysSectorSize   = sysSectorSize;
    this.blockNum16Bit = blockNum16Bit;
    this.blockSize     = blockSize;
    this.dirBlocks     = dirBlocks;
    this.begDirArea    = sysTracks * sides * sysSectPerTrack * sysSectorSize;

    this.diskBuf = new byte[ this.begDirArea
	+ ((cyls - sysTracks) * sides * sectPerTrack * sectorSize) ];
    this.begFileArea = this.begDirArea + (this.dirBlocks * this.blockSize);
    this.dstDirPos   = this.begDirArea;
    this.dstFilePos  = this.begFileArea;
    this.blockNum    = this.dirBlocks;
    this.begTimeFile = -1;
    this.dstTimeFile = -1;
    this.endTimeFile = -1;
    this.calendar    = null;
    if( this.begDirArea > 0 ) {
      Arrays.fill(
		this.diskBuf,
		0,
		Math.min( this.begDirArea, this.diskBuf.length ),
		(byte) 0x00 );
    }
    if( this.begDirArea < this.diskBuf.length ) {
      Arrays.fill(
		this.diskBuf,
		this.begDirArea,
		this.diskBuf.length,
		(byte) 0xE5 );
    }
    if( dateStamper ) {
      byte[] timeBytes = new byte[ this.dirBlocks * this.blockSize / 2 ];
      Arrays.fill( timeBytes, (byte) 0x00 );
      String text    = "!!!TIME\u0092";
      int    textLen = text.length();
      int    srcPos  = 0;
      int    dstPos  = 0x0F;
      while( dstPos < timeBytes.length ) {
	if( srcPos >= textLen ) {
	  srcPos = 0;
	}
	timeBytes[ dstPos ] = (byte) text.charAt( srcPos++ );
	dstPos += 0x10;
      }
      try( ByteArrayInputStream in = new ByteArrayInputStream( timeBytes ) ) {
	this.begTimeFile = this.dstFilePos;
	addFile(
		0,
		DateStamper.FILENAME,
		in,
		false,
		false,
		false,
		null );
	this.dstTimeFile = this.begTimeFile + 0x10;
	this.endTimeFile = this.dstFilePos;
      }
      this.calendar = Calendar.getInstance();
    }
  }


  public void addFile(
		int     userNum,
		String  entryName,
		File    file,
		boolean readOnly,
		boolean sysFile,
		boolean archived ) throws IOException
  {
    if( (entryName != null) && (file != null) ) {
      if( (this.begTimeFile >= 0)
	  && entryName.equals( DateStamper.FILENAME ) )
      {
	throw new IOException( LangUtil.getText(
		"disk.text.disk_format_supports",
		DateStamper.FILENAME ) );
      }
      InputStream in = null;
      try {
	in = new BufferedInputStream( new FileInputStream( file ) );
	addFile(
		userNum,
		entryName,
		in,
		readOnly,
		sysFile,
		archived,
		FileTimesData.createOf( file ) );
      }
      finally {
	EmuUtil.closeSilently( in );
      }
    }
  }


  public AbstractFloppyDisk createDisk( Frame owner )
  {
    // DateStamper Pruefsummen berechnen
    if( (this.begTimeFile >= 0) && (this.begTimeFile < this.endTimeFile) ) {
      int pos = this.begTimeFile;
      while( pos < this.endTimeFile ) {
	int cks = 0;
	for( int i = 0; (i < 0x7F) && (pos < this.endTimeFile); i++ ) {
	  cks += ((int) this.diskBuf[ pos++ ] & 0xFF);
	}
	if( pos < this.endTimeFile ) {
	  this.diskBuf[ pos++ ] = (byte) cks;
	}
      }
    }
    return new Disk(
		owner,
		this.cyls,
		this.sides,
		this.sectPerTrack,
		this.sectorSize,
		this.interleave,
		this.sysTracks,
		this.sysSectPerTrack,
		this.sysSectorSize,
		this.diskBuf );
  }


  public void fillSysTracks( File file ) throws IOException
  {
    if( file != null ) {
      InputStream in = null;
      try {
	in    = new BufferedInputStream( new FileInputStream( file ) );
	int p = 0;
	int b = in.read();
	while( b >= 0 ) {
	  if( p >= this.begDirArea ) {
	    throw new IOException(
			LangUtil.getText( "disk.error.file_system_tracks" ) );
	  }
	  this.diskBuf[ p++ ] = (byte) b;
	  b = in.read();
	}
      }
      finally {
	EmuUtil.closeSilently( in );
      }
    }
  }


	/* --- private Methoden --- */

  private int addDirEntry(
			int           userNum,
			String        entryName,
			boolean       readOnly,
			boolean       sysFile,
			boolean       archived,
			FileTimesData fileTimesData,
			int           extentNum ) throws IOException
  {
    if( this.dstDirPos >= this.begFileArea ) {
      throw new IOException( LangUtil.getText( "disk.error.directory_full" ) );
    }

    int entryBegPos                  = this.dstDirPos;
    this.diskBuf[ this.dstDirPos++ ] = (byte) (userNum & 0x0F);

    int[] sizes = { 8, 3 };
    int   len   = entryName.length();
    int   pos   = 0;
    for( int i = 0; i < sizes.length; i++ ) {
      int n = sizes[ i ];
      while( (pos < len) && (n > 0) ) {
	char ch = entryName.charAt( pos++ );
	if( ch == '.' ) {
	  break;
	}
	this.diskBuf[ this.dstDirPos++ ] = (byte) ch;
	--n;
      }
      while( (n > 0) ) {
	this.diskBuf[ this.dstDirPos++ ] = (byte) '\u0020';
	--n;
      }
      if( pos < len ) {
	if( entryName.charAt( pos ) == '.' ) {
	  pos++;
	}
      }
    }
    this.diskBuf[ this.dstDirPos++ ] = (byte) (extentNum & 0x1F);
    this.diskBuf[ this.dstDirPos++ ] = (byte) 0;
    this.diskBuf[ this.dstDirPos++ ] = (byte) ((extentNum >> 5) & 0x3F);

    if( readOnly ) {
      this.diskBuf[ entryBegPos + 9 ] |= 0x80;
    }
    if( sysFile ) {
      this.diskBuf[ entryBegPos + 10 ] |= 0x80;
    }
    if( archived ) {
      this.diskBuf[ entryBegPos + 11 ] |= 0x80;
    }
    for( int i = 0; i < 17; i++ ) {
      this.diskBuf[ this.dstDirPos++ ] = (byte) 0;
    }
    if( (this.dstTimeFile >= 0) && (this.dstTimeFile < this.endTimeFile) ) {
      writeTimeEntry( fileTimesData.getCreationMillis() );
      writeTimeEntry( fileTimesData.getLastAccessMillis() );
      writeTimeEntry( fileTimesData.getLastModifiedMillis() );
      this.dstTimeFile++;
    }
    return entryBegPos;
  }


  private void addFile(
		int           userNum,
		String        entryName,
		InputStream   in,
		boolean       readOnly,
		boolean       sysFile,
		boolean       archived,
		FileTimesData fileTimesData ) throws IOException
  {
    int extentNum  = 0;
    int b          = in.read();
    if( b >= 0 ) {
      int extentsPerDirEntry = CPMDirUtil.getExtentsPerDirEntry(
							blockSize,
							blockNum16Bit );
      while( b >= 0 ) {
	if( extentNum >= 0x800 ) {
	  throwFileTooBig( entryName );
	}
	int dirPos = addDirEntry(
				userNum,
				entryName,
				readOnly,
				sysFile,
				archived,
				fileTimesData,
				extentNum );
	int blkPos = dirPos + 0x10;

	// Datenbloecke des Directory-Eintrags schreiben
	int begEntryDataPos       = this.dstFilePos;
	int endEntryDataPos       = begEntryDataPos;
	int remainEntryDataBlocks = (this.blockNum16Bit ? 8 : 16);
	while( (remainEntryDataBlocks > 0) && (b >= 0) ) {
	  int remainBlockBytes = this.blockSize;
	  while( (remainBlockBytes > 0) && (b >= 0) ) {
	    if( this.dstFilePos >= this.diskBuf.length ) {
	      throw new IOException(
		LangUtil.getText( "disk.error.selected_disk_format" ) );
	    }
	    this.diskBuf[ this.dstFilePos++ ] = (byte) b;
	    --remainBlockBytes;
	    b = in.read();
	  }
	  endEntryDataPos    = this.dstFilePos;
	  int endOf128BlkPos = (this.dstFilePos + 127) & ~0x7F;
	  if( endOf128BlkPos > this.diskBuf.length ) {
	    endOf128BlkPos = this.diskBuf.length;
	  }
	  while( (remainBlockBytes > 0)
		 && (this.dstFilePos < endOf128BlkPos) )
	  {
	    this.diskBuf[ this.dstFilePos++ ] = (byte) 0x1A;
	    --remainBlockBytes;
	  }
	  while( (remainBlockBytes > 0)
		 && (this.dstFilePos < this.diskBuf.length) )
	  {
	    this.diskBuf[ this.dstFilePos++ ] = (byte) 0x00;
	    --remainBlockBytes;
	  }
	  if( this.blockNum16Bit ) {
	    this.diskBuf[ blkPos++ ] = (byte) (this.blockNum & 0xFF);
	    this.diskBuf[ blkPos++ ] = (byte) ((this.blockNum >> 8) & 0xFF);
	    this.blockNum++;
	    if( this.blockNum > 0xFFFF ) {
	      throwFileTooBig( entryName );
	    }
	  } else {
	    this.diskBuf[ blkPos++ ] = (byte) this.blockNum++;
	    if( this.blockNum > 0xFF ) {
	      throwFileTooBig( entryName );
	    }
	  }
	  --remainEntryDataBlocks;
	}
	int nEntrySegs  = (endEntryDataPos - begEntryDataPos + 127) / 128;
	int nExtentSegs = nEntrySegs % 128;
	if( (nEntrySegs > 0) && (nExtentSegs == 0) ) {
	  nExtentSegs = 128;
	}
	int nEntryExtents = (nEntrySegs + 127) / 128;
	int tmpExtentNum  = extentNum;
	if( (nEntryExtents > 1) && (nEntryExtents <= extentsPerDirEntry) ) {
	  tmpExtentNum = extentNum + nEntryExtents - 1;
	}
	this.diskBuf[ dirPos + 12 ] = (byte) (tmpExtentNum & 0x1F);
	this.diskBuf[ dirPos + 14 ] = (byte) ((tmpExtentNum >> 5) & 0x3F);
	this.diskBuf[ dirPos + 15 ] = (byte) nExtentSegs;

	extentNum += extentsPerDirEntry;
      }
    } else {
      // leere Datei
      addDirEntry(
		userNum,
		entryName,
		readOnly,
		sysFile,
		archived,
		fileTimesData,
		extentNum );
    }
  }


  private static void throwFileTooBig( String entryName ) throws IOException
  {
    throw new IOException( entryName + ": Datei zu gro\u00DF!" );
  }


  private static byte toBcdByte( int value )
  {
    return (byte) (((((value / 10) % 10) << 4) & 0xF0)
				| ((value % 10) & 0x0F));
  }


  private void writeTimeEntry( Long millis )
  {
    if( (this.dstTimeFile + 4) < this.endTimeFile ) {
      boolean done = false;
      if( (this.calendar != null) && (millis != null) ) {
	this.calendar.clear();
	this.calendar.setTimeInMillis( millis.longValue() );
	int year = this.calendar.get( Calendar.YEAR );
	if( (year >= 1978) && (year < 2078) ) {
	  this.diskBuf[ this.dstTimeFile++ ] = toBcdByte( year );
	  this.diskBuf[ this.dstTimeFile++ ] =
		toBcdByte( this.calendar.get( Calendar.MONTH ) + 1 );
	  this.diskBuf[ this.dstTimeFile++ ] =
		toBcdByte( this.calendar.get( Calendar.DAY_OF_MONTH ) );
	  this.diskBuf[ this.dstTimeFile++ ] =
		toBcdByte( this.calendar.get( Calendar.HOUR_OF_DAY ) );
	  this.diskBuf[ this.dstTimeFile++ ] =
		toBcdByte( this.calendar.get( Calendar.MINUTE ) );
	  done = true;
	}
      }
      if( !done ) {
	this.dstTimeFile += 5;
      }
    }
  }
}
