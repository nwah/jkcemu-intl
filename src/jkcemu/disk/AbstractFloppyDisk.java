/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Basisklasse zur Emulation einer Diskette
 */

package jkcemu.disk;

import java.awt.EventQueue;
import java.awt.Frame;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import jkcemu.base.EmuUtil;
import jkcemu.lang.LangUtil;


public abstract class AbstractFloppyDisk
{
  public enum Density { UNKNOWN, SD_DD, HD, ED };
  public enum DriveType { UNKNOWN, INCH_8, INCH_5_25, INCH_3_5 };
  public enum RecordingMode { UNKNOWN, FM, MFM };
  public enum Stepping { UNKNOWN, SINGLE_STEP, DOUBLE_STEP, EVEN_ONLY_STEP };
  public enum TransferRate { UNKNOWN, KBPS_250, KBPS_300, KBPS_500 };

  public static final String PROP_CYLINDERS         = "cylinders";
  public static final String PROP_FILE              = "file";
  public static final String PROP_READONLY          = "readonly";
  public static final String PROP_RESOURCE          = "resource";
  public static final String PROP_SIDES             = "sides";

  private Frame              owner;
  private volatile int       cyls;
  private volatile int       sides;
  private volatile int       interleave;
  private volatile int       diskSize;
  private volatile Density   checkedDiskDensity;
  private volatile String    fmtText;
  private volatile boolean   fmtTextWithSectorSize;
  private String             mediaText;
  private String             warningText;
  private boolean            repaired;
  private Map<Integer,int[]> sectPerTrack2SectIdx;


  protected AbstractFloppyDisk(
			Frame owner,
			int   cyls,
			int   sides,
			int   interleave )
  {
    this.owner                = owner;
    this.cyls                 = cyls;
    this.sides                = sides;
    this.interleave           = interleave;
    this.diskSize             = -1;
    this.checkedDiskDensity   = null;
    this.fmtText              = null;
    this.mediaText            = null;
    this.warningText          = null;
    this.repaired             = false;
    this.sectPerTrack2SectIdx = new HashMap<>();
  }


  protected AbstractFloppyDisk(
			Frame owner,
			int   cyls,
			int   sides )	// -1 wenn nicht eindeutig
  {
    this( owner, cyls, sides, 0 );
  }


  protected void clear()
  {
    this.cyls        = 0;
    this.sides       = 0;
    this.diskSize    = -1;
    this.fmtText     = null;
    this.mediaText   = null;
    this.warningText = null;
    this.repaired    = false;
    this.sectPerTrack2SectIdx.clear();
  }


  public void closeSilently()
  {
    // leer
  }


  protected Density determineDiskDensity()
  {
    Density diskDensity = Density.SD_DD;
    int     sides       = getSides();
    int     cyls        = getCylinders();
    for( int head = 0; head < sides; head++ ) {
      for( int cyl = 0; cyl < cyls; cyl++ ) {
	Set<String> idStrings    = new TreeSet<>();
	int         trackSize    = 0;
	int         trackSectors = getTrackSectorCount( cyl, head );
	for( int i = 0; i < trackSectors; i++ ) {
	  SectorData sector = getSectorByIndex( cyl, head, i );
	  if( sector != null ) {
	    if( idStrings.add( sector.getIDString() ) ) {
	      trackSize += SectorData.getSizeBySizeCode(
						sector.getSizeCode() );
	    }
	  }
	}
	Density trackSensity = getDensityByTrackSize( trackSize );
	if( trackSensity == Density.ED ) {
	  diskDensity = trackSensity;
	  break;
	}
	if( (trackSensity == Density.HD)
	    && (diskDensity == Density.SD_DD) )
	{
	  diskDensity = trackSensity;
	}
      }
      if( diskDensity == Density.ED ) {
	break;
      }
    }
    return diskDensity;
  }


  protected TransferRate determineDiskTransferRate()
  {
    // haeufigste Transferrate der einzelnen Spuren ermitteln
    Map<TransferRate,AtomicInteger> tr2Cnt = new HashMap<>();
    int                             nFM    = 0;
    int                             sides  = getSides();
    int                             cyls   = getCylinders();
    for( int head = 0; head < sides; head++ ) {
      for( int cyl = 0; cyl < cyls; cyl++ ) {
	TransferRate  tr  = getTrackTransferRate( cyl, head );
	AtomicInteger cnt = tr2Cnt.get( tr );
	if( cnt != null ) {
	  cnt.incrementAndGet();
 	} else {
	  tr2Cnt.put( tr, new AtomicInteger( 1 ) );
	}
	if( getTrackRecordingMode( cyl, head ) == RecordingMode.FM ) {
	  nFM++;
	}
      }
    }
    int          nTransferRate = 0;
    TransferRate transferRate  = TransferRate.UNKNOWN;
    for( Map.Entry<TransferRate,AtomicInteger> e : tr2Cnt.entrySet() ) {
      if( e.getValue().intValue() > nTransferRate ) {
	transferRate  = e.getKey();
	nTransferRate = e.getValue().intValue();
      }
    }

    /*
     * Wenn in den Spuren keine TransferRate bekannt ist,
     * die Mehrheit der Spuren aber FM hat
     * oder es sich um eine 8''-Diskette handelt,
     * ist die TransferRate 500 kpbs.
     */
    DriveType driveType = getDriveType();
    if( (transferRate == TransferRate.UNKNOWN)
	&& (((nFM * 2) > (sides * cyls))
	    || (driveType == DriveType.INCH_8)) )
    {
      transferRate = transferRate.KBPS_500;
    } else if( driveType == DriveType.UNKNOWN ) {
      if( determineDriveType() == DriveType.INCH_8 ) {
	transferRate = transferRate.KBPS_500;
      }
    }

    /*
     * Wenn die TransferRate immer noch nicht bestlimmt werden konne,
     * wird sie aus der Dichte abgeleitet.
     */
    if( transferRate == TransferRate.UNKNOWN ) {
      Density density = getCheckedDiskDensity();
      if( (density == Density.HD) || (density == Density.ED) ) {
	transferRate = transferRate.KBPS_300;
      } else {
	transferRate = transferRate.KBPS_250;
      }
    }
    return transferRate;
  }


  protected RecordingMode determineDiskRecordingMode()
  {
    int nFM   = 0;
    int nMFM  = 0;
    int sides = getSides();
    int cyls  = getCylinders();
    for( int head = 0; head < sides; head++ ) {
      for( int cyl = 0; cyl < cyls; cyl++ ) {
	switch( getTrackRecordingMode( cyl, head ) ) {
	  case FM:
	    nFM++;
	    break;
	  case MFM:
	    nMFM++;
	    break;
	}
      }
    }
    RecordingMode recordingMode = RecordingMode.UNKNOWN;
    if( (nFM > 0) && (nFM > nMFM) ) {
      recordingMode = RecordingMode.FM;
    } else if( (nMFM > 0) && (nMFM > nFM) ) {
      recordingMode = RecordingMode.MFM;
    }
    if( recordingMode == RecordingMode.UNKNOWN ) {
      if( getDriveType() == DriveType.INCH_8 ) {
	recordingMode = RecordingMode.FM;
      } else {
	recordingMode = RecordingMode.MFM;
      }
    }
    return recordingMode;
  }


  protected DriveType determineDriveType()
  {
    DriveType driveType = DriveType.INCH_5_25;
    if( getCylinders() == 77 ) {
      driveType = DriveType.INCH_8;
    } else if( (getMostCommonSectorSize( null, null ) == 512)
	       || (getDiskSize() >= (1.44 * 1024 * 1024)) )
    {
      driveType = DriveType.INCH_3_5;
    }
    return driveType;
  }


  /*
   * Die Methode wird (mehrfach) aufgerufen,
   * wenn sich beim Formatieren der Diskette das Format geaendert hat.
   */
  protected void diskFormatChanged()
  {
    this.diskSize           = -1;
    this.checkedDiskDensity = null;
    this.fmtText            = null;
    this.warningText        = null;
    this.repaired           = false;
    if( this.owner instanceof FloppyDiskStationFrm ) {
      ((FloppyDiskStationFrm) owner).fireDiskFormatChanged( this );
    }
  }


  protected void fireShowError( final String msg, final Exception ex )
  {
    if( this.owner instanceof FloppyDiskStationFrm ) {
      ((FloppyDiskStationFrm) this.owner).fireShowDiskError( this, msg, ex );
    } else {
      EmuUtil.fireShowErrorDlg( owner, msg, ex );
    }
  }


  protected void fireShowReadError(
				int       cyl,
				int       head,
				int       sectorNum,
				Exception ex )
  {
    fireShowError(
	String.format(
		"Sektor [C=%d,H=%d,R=%d] kann nicht gelesen werden",
		cyl,
		head,
		sectorNum ),
	ex );
  }


  protected void fireShowWriteError(
				int       cyl,
				int       head,
				int       sectorNum,
				Exception ex )
  {
    fireShowError(
	String.format(
		"Sektor [C=%d,H=%d,R=%d] kann nicht geschrieben werden",
		cyl,
		head,
		sectorNum ),
	ex );
  }


  /*
   * Diese Methode dient zum Formatieren einer Spur
   * und liefert im Erfolgsfall true zurueck.
   */
  public boolean formatTrack(
			int        physCyl,
			int        physHead,
			SectorID[] sectorIDs,
			byte[]     dataBuf,
			boolean    mfmMode )
  {
    boolean rv = false;
    if( !isReadOnly() && (sectorIDs != null) ) {
      if( sectorIDs.length > 0 ) {
	rv = true;
	for( int i = 0; i < sectorIDs.length; i++ ) {
	  boolean    state  = false;
	  SectorID sectorID = sectorIDs[ i ];
	  if( sectorID != null ) {
	    SectorData sector = getSectorByID(
					physCyl,
					physHead,
					sectorID.getCylinder(),
					sectorID.getHead(),
					sectorID.getSectorNum(),
					sectorID.getSizeCode() );
	    if( sector != null ) {
	      state = writeSector(
				physCyl,
				physHead,
				sector,
				dataBuf,
				dataBuf.length,
				false );
	    } else {
	      fireShowError(
			LangUtil.getText( "disk.error.formatting_already" ),
			null );
	    }
	  }
	  if( !state ) {
	    rv = false;
	    break;
	  }
	}
      }
    }
    return rv;
  }


  public Density getCheckedDiskDensity()
  {
    if( this.checkedDiskDensity == null ) {
      Density density = getDiskDensity();
      if( density == Density.UNKNOWN ) {
	density = determineDiskDensity();
      }
      this.checkedDiskDensity = density;
    }
    return this.checkedDiskDensity;
  }


  public int getCylinders()
  {
    return this.cyls;
  }


  public static Density getDensityByTrackSize( int trackSize )
  {
    Density density = Density.SD_DD;
    if( trackSize > (12500 * 1024) ) {
      density = Density.ED;
    } else if( trackSize > (6250 * 1024) ) {
      density = Density.HD;
    }
    return density;
  }


  public java.util.Date getDiskDate()
  {
    return null;
  }


  public Density getDiskDensity()
  {
    return Density.UNKNOWN;
  }


  public RecordingMode getDiskRecordingMode()
  {
    return RecordingMode.UNKNOWN;
  }


  public int getDiskSize()
  {
    return getDiskSize( null, null );
  }


  protected int getDiskSize(
			Set<Integer> sectorsPerTracksOut,
			Set<Integer> sectorSizesOut )
  {
    if( (this.diskSize < 0)
	|| (sectorsPerTracksOut != null)
	|| (sectorSizesOut != null) )
    {
      int diskSize = 0;
      if( (this.cyls > 0) && (this.sides > 0) ) {
	for( int cyl = 0; cyl < this.cyls; cyl++ ) {
	  for( int head = 0; head < this.sides; head++ ) {
	    Set<Integer> sectorNums = new TreeSet<>();
	    int          sectorIdx  = 0;
	    SectorData   sectorData = null;
	    for(;;) {
	      sectorData = getSectorByIndex( cyl, head, sectorIdx++ );
	      if( sectorData == null ) {
		break;
	      }
	      if( sectorNums.add( sectorData.getSectorNum() ) ) {
		int sectorSize = sectorData.getDataLength();
		diskSize += sectorSize;
		if( sectorSizesOut != null ) {
		  sectorSizesOut.add( sectorSize );
		}
	      }
	    }
	    if( sectorsPerTracksOut != null ) {
	      sectorsPerTracksOut.add( sectorNums.size() );
	    }
	  }
	}
      }
      this.diskSize = diskSize;
    }
    return this.diskSize;
  }


  public TransferRate getDiskTransferRate()
  {
    return TransferRate.UNKNOWN;
  }


  public DriveType getDriveType()
  {
    return DriveType.UNKNOWN;
  }


  public abstract String getFileFormatText();


  public String getFormatText()
  {
    if( this.fmtText == null ) {
      StringBuilder buf            = new StringBuilder( 128 );
      boolean       withSectorSize = false;
      if( (this.cyls > 0) && (this.sides > 0) ) {
	SortedSet<Integer> sectorsPerTracks = new TreeSet<>();
	SortedSet<Integer> sectorSizes      = new TreeSet<>();
	int                diskSize         = getDiskSize(
							sectorsPerTracks,
							sectorSizes );
	int kBytes = diskSize / 1024;
	if( sectorsPerTracks.size() > 1 ) {
	  sectorsPerTracks.remove( 0 );
	}
	if( (sectorsPerTracks.size() == 1)
	    && (sectorSizes.size() == 1) )
	{
	  int sectorsPerTrack = sectorsPerTracks.first();
	  int sectorSize      = sectorSizes.first();
	  int sysTracks = getSysTracks();
	  if( (sysTracks > 0) && (sysTracks < this.cyls) ) {
	    buf.append(
		LangUtil.getText( "disk.text.kbyte_tracks_bytes_net",
			(this.cyls - sysTracks) * this.sides
				* sectorsPerTrack * sectorSize / 1024,
			kBytes,
			this.cyls,
			sectorsPerTrack,
			sectorSize ) );
	  } else {
	    buf.append(
		LangUtil.getText( "disk.text.kbyte_tracks_bytes",
			kBytes,
			this.cyls,
			sectorsPerTrack,
			sectorSize ) );
	  }
	  withSectorSize = true;
	} else {
	  buf.append(
		LangUtil.getText( "disk.text.kbyte_tracks",
			kBytes,
			this.cyls ) );
	}
	switch( sides ) {
	  case 1:
	    buf.append( ", " );
	    buf.append( LangUtil.getText( "disk.text.single_sided" ) );
	    break;
	  case 2:
	    buf.append( ", " );
	    buf.append( LangUtil.getText( "disk.text.double_sided" ) );
	    break;
	}
      } else {
	buf.append( LangUtil.getText( "disk.text.unformatted" ) );
      }
      this.fmtTextWithSectorSize = withSectorSize;
      this.fmtText = buf.toString();
    }
    return this.fmtText;
  }


  public boolean isFormatTextWithSectorSize()
  {
    getFormatText();
    return this.fmtTextWithSectorSize;
  }


  public String getMediaText()
  {
    return this.mediaText;
  }


  public int getMostCommonSectorSize(
			AtomicInteger rvOccurence,
			AtomicInteger rvTotalSectorCnt )
  {
    int totalSectorCnt = 0;
    Map<Integer,Integer> sectorSize2Cnt = new HashMap<>();
    for( int cyl = 0; cyl < this.cyls; cyl++ ) {
      for( int head = 0; head < this.sides; head++ ) {
	int trackSectors = getTrackSectorCount( cyl, head );
	for( int i = 0; i < trackSectors; i++ ) {
	  SectorData sector = getSectorByIndex( cyl, head, i );
	  if( sector != null ) {
	    int     size = sector.getDataLength();
	    Integer cnt  = sectorSize2Cnt.get( size );
	    if( cnt != null ) {
	      cnt = Integer.valueOf( cnt.intValue() + 1 );
	    } else {
	      cnt = Integer.valueOf( 1 );
	    }
	    sectorSize2Cnt.put( size, cnt );
	    totalSectorCnt++;
	  }
	}
      }
    }
    int sectorSize = 0;
    int sectorCnt  = 0;
    for( Integer size : sectorSize2Cnt.keySet() ) {
      Integer cnt = sectorSize2Cnt.get( size );
      if( cnt != null ) {
	if( cnt.intValue() > sectorCnt ) {
	  sectorSize = size;
	  sectorCnt  = cnt;
	}
      }
    }
    if( rvTotalSectorCnt != null ) {
      rvTotalSectorCnt.set( totalSectorCnt );
    }
    if( rvOccurence != null ) {
      rvOccurence.set( sectorCnt );
    }
    return sectorSize;
  }


  public String getRemark()
  {
    return null;
  }


  /*
   * Diese Methode liefert einen Sektor anhand
   * seiner physischen Position auf der Spur.
   */
  public abstract SectorData getSectorByIndex(
					int physCyl,
					int physHead,
					int sectorIdx );


  /*
   * Diese Methode liefert einen Sektor anhand seiner Sektor-ID.
   *
   * Anstelle von sizeCode kann auch -1 uebergeben werden,
   * d.h. sizeCode wird dann nicht verglichen.
   *
   * Die Standard-Implementierung sucht den Sektor zuerst auf seiner
   * wahrscheinlichsten Position.
   * Ist er dort nicht zu finden,
   * werden alle Sektoren der Spur in die Suche einbezogen.
   */
  public SectorData getSectorByID(
				int physCyl,
				int physHead,
				int cyl,
				int head,
				int sectorNum,
				int sizeCode )
  {
    int        idx = sectorNum - 1;
    SectorData rv  = getSectorByIndex( physCyl, physHead, idx );
    if( rv != null ) {
      if( (rv.getCylinder() != cyl)
	  || (rv.getHead() != head)
	  || (rv.getSectorNum() != sectorNum)
	  || ((sizeCode >= 0) && (rv.getSizeCode() != sizeCode)) )
      {
	rv = null;
      }
    }
    if( rv == null ) {
      int n = getTrackSectorCount( physCyl, physHead );
      for( int i = 0; i < n; i++ ) {
	if( i != idx ) {
	  SectorData sector = getSectorByIndex( physCyl, physHead, i );
	  if( sector != null ) {
	    if( (sector.getCylinder() == cyl)
		&& (sector.getHead() == head)
		&& (sector.getSectorNum() == sectorNum)
		&& ((sizeCode < 0) || (sector.getSizeCode() == sizeCode)) )
	    {
	      rv = sector;
	      break;
	    }
	  }
	}
      }
    }
    return rv;
  }


  public int getSides()
  {
    return sides;
  }


  public SortedSet<SectorData> getSortedTrackSectors( int cyl, int head )
  {
    SortedSet<SectorData> sectorSet = new TreeSet<>();
    int                   sectorIdx = 0;
    for(;;) {
      SectorData sector = getSectorByIndex( cyl, head, sectorIdx++ );
      if( sector == null ) {
	break;
      }
      /*
       * Sollte die gleiche Sektor-ID mehrfach vorhenden sein,
       * dann den fehlerfreien Sektor nehmen
       */
      if( sectorSet.contains( sector ) ) {
	if( !sector.checkError() ) {
	  sectorSet.remove( sector );
	}
      }
      sectorSet.add( sector );
    }
    return sectorSet;
  }


  public Stepping getStepping()
  {
    return Stepping.UNKNOWN;
  }


  protected int getSysTracks()
  {
    return 0;
  }


  public Density getTrackDensity( int physCyl, int physHead )
  {
    return Density.UNKNOWN;
  }


  public RecordingMode getTrackRecordingMode( int physCyl, int physHead )
  {
    return RecordingMode.UNKNOWN;
  }


  public abstract int getTrackSectorCount( int physCyl, int physHead );


  public TransferRate getTrackTransferRate( int physCyl, int physHead )
  {
    return TransferRate.UNKNOWN;
  }


  public String getWarningText()
  {
    return LangUtil.getText( this.warningText );
  }


  public static boolean isDiskFileHeader( byte[] header )
  {
    return CopyQMDisk.isCopyQMFileHeader( header )
		|| CPCDisk.isCPCDiskFileHeader( header )
		|| ImageDisk.isImageDiskFileHeader( header )
		|| TeleDisk.isTeleDiskFileHeader( header );
  }


  public boolean isReadOnly()
  {
    return true;
  }


  public boolean isRepaired()
  {
    return this.repaired;
  }


  public void putSettingsTo( Properties props, String prefix )
  {
    if( props != null ) {
      props.setProperty(
		prefix + PROP_READONLY,
		Boolean.toString( isReadOnly() ) );
      props.setProperty(
		prefix + PROP_CYLINDERS,
		Integer.toString( getCylinders() ) );
      props.setProperty(
		prefix + PROP_SIDES,
		Integer.toString( getSides() ) );
    }
  }


  protected int sectorIndexToInterleave( int sectorIdx, int sectorsPerTrack )
  {
    int rv = sectorIdx;
    if( (this.interleave > 1)
	&& (this.interleave < sectorsPerTrack)
	&& (sectorsPerTrack > 2) )
    {
      int[] sectorIdxMap = this.sectPerTrack2SectIdx.get( sectorsPerTrack );
      if( sectorIdxMap == null ) {
	sectorIdxMap = new int[ sectorsPerTrack ];
	Arrays.fill( sectorIdxMap, -1 );
	int srcIdx = 0;
	int dstIdx = 0;
	while( srcIdx < sectorsPerTrack ) {
	  while( sectorIdxMap[ dstIdx ] >= 0 ) {
	    dstIdx = (dstIdx + 1) % sectorsPerTrack;
	  }
	  sectorIdxMap[ dstIdx ] = srcIdx++;
	  dstIdx = (dstIdx + this.interleave) % sectorsPerTrack;
	}
	this.sectPerTrack2SectIdx.put( sectorsPerTrack, sectorIdxMap );
      }
      if( (sectorIdx >= 0) && (sectorIdx < sectorIdxMap.length) ) {
	sectorIdx = sectorIdxMap[ sectorIdx ];
      }
    }
    return sectorIdx;
  }


  public void setMediaText( String text )
  {
    this.mediaText = text;
  }


  public void setOwner( Frame owner )
  {
    this.owner = owner;
  }


  public void setRepaired( boolean state )
  {
    this.repaired = state;
  }


  public void setWarningText( String text )
  {
    this.warningText = text;
  }


  public boolean supportsDeletedDataSectors()
  {
    return false;
  }


  protected static void throwEmptyFirstTrack() throws IOException
  {
    throw new IOException(
		LangUtil.getText( "disk.error.first_track_contains" ) );
  }


  protected static void throwSectorSpaceTooSmall(
				int cyl,
				int head,
				int sectorNum ) throws IOException
  {
    throw new IOException(
	String.format(
		"Seite %d, Spur %d, Sektor %d: Datenfeld zu klein,\n"
			+ "Der Sektor kann nicht geschrieben werden,\n"
			+ "weil das Datenfeld des Sektors kleiner ist,\n"
			+ "als es laut Diskettenformat sein m\u00FCsste.",
		head - 1,
		cyl,
		sectorNum ) );
  }


  protected static void throwUnexpectedEOF() throws IOException
  {
    throw new IOException(
		LangUtil.getText( "disk.error.unexpected_end_file" ) );
  }


  protected void trackExists( int cyl, int head )
  {
    if( cyl >= this.cyls ) {
      this.cyls = cyl + 1;
    }
    if( head >= this.sides ) {
      this.sides = head + 1;
    }
  }


  /*
   * Diese Methode dient zum Schreiben eines Sektors
   * und liefert im Erfolgsfall true zurueck.
   * Die Sektordaten stehen in dem Byte-Array.
   * Konnte der Sektor geschrieben werden,
   * muessen die Daten auch in das Sektorobjekt kopiert werden.
   */
  public boolean writeSector(
			int        physCyl,
			int        physHead,
			SectorData sector,
			byte[]     dataBuf,
			int        dataLen,
			boolean    dataDeleted )
  {
    return false;
  }


	/* --- private Methoden --- */

  private static String createSectorKey(
				int physCyl,
				int physHead,
				int sectorIdx )
  {
    return String.format(
			"%d:%d:%d",
			physCyl,
			physHead,
			sectorIdx );
  }
}
