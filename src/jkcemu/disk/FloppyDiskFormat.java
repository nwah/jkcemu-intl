/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Format einer Diskette
 */

package jkcemu.disk;

import jkcemu.lang.LangUtil;


public class FloppyDiskFormat
{
  public static final FloppyDiskFormat FMT_400K
	= new FloppyDiskFormat(
		80, 1, 5, 1024, 1,
		0, 2, 2048, true, false,
		"disk.text.400k_llc2_cp" );

  public static final FloppyDiskFormat FMT_624K
	= new FloppyDiskFormat(
		80, 2, 16, 256, 1,
		2, 2, 2048, true, false,
		"disk.text.640k_624k_pc" );

  public static final FloppyDiskFormat FMT_702K_I3_DS
	= new FloppyDiskFormat(
		80, 2, 9, 512, 3,
		2, 2, 2048, true, true,
		"disk.text.720k_702k_interleave" );

  public static final FloppyDiskFormat FMT_711K_I5_BASDOS
	= new FloppyDiskFormat(
		80, 2, 9, 512, 5,
		1, 1, 4096, false, false,
		"disk.text.720k_711k_kc" );

  public static final FloppyDiskFormat FMT_720K
	= new FloppyDiskFormat(
		80, 2, 9, 512, 1,
		0, 2, 2048, true, false,
		"disk.text.720k_a5105_rbasic" );

  public static final FloppyDiskFormat FMT_780K
	= new FloppyDiskFormat(
		80, 2, 5, 1024, 1,
		2, 2, 2048, true, false,
		"disk.text.800k_780k_caos" );

  public static final FloppyDiskFormat FMT_780K_I2
	= new FloppyDiskFormat(
		80, 2, 5, 1024, 2,
		2, 2, 2048, true, false,
		"disk.text.800k_780k_interleave_2_1_a5105" );

  public static final FloppyDiskFormat FMT_780K_I3
	= new FloppyDiskFormat(
		80, 2, 5, 1024, 3,
		2, 2, 2048, true, false,
		"disk.text.800k_780k_interleave_3_1_microdos" );

  public static final FloppyDiskFormat FMT_780K_I3_DS
	= new FloppyDiskFormat(
		80, 2, 5, 1024, 3,
		2, 2, 2048, true, true,
		"disk.text.800k_780k_interleave_3_1_datestamper" );

  public static final FloppyDiskFormat FMT_800K_I4
	= new FloppyDiskFormat(
		80, 2, 5, 1024, 4,
		0, 3, 2048, true, false,
		"disk.text.800k_interleave_4" );

  public static final FloppyDiskFormat FMT_1200K
	= new FloppyDiskFormat(
		80, 2, 15, 512, 1,
		0, 2, 4096, true, false,
		"disk.text.1200k_5_25" );

  public static final FloppyDiskFormat FMT_1440K
	= new FloppyDiskFormat(
		80, 2, 18, 512, 1,
		0, 2, 4096, true, false,
		"disk.text.1440k_3_5" );

  public static final FloppyDiskFormat FMT_1738K_I3_DS
	= new FloppyDiskFormat(
		80, 2, 11, 1024, 3,
		1, 2, 4096, true, true,
		"disk.text.1760k_1738k_interleave" );

  private static final FloppyDiskFormat[] formats = {
			new FloppyDiskFormat( 80, 2,  9,  512 ),
			new FloppyDiskFormat( 80, 2,  5, 1024 ),
			new FloppyDiskFormat( 80, 2, 11, 1024 ),
			new FloppyDiskFormat( 80, 2, 15,  512 ),
			new FloppyDiskFormat( 80, 2, 18,  512 ),
			new FloppyDiskFormat( 80, 2, 11, 1024 ),
			new FloppyDiskFormat( 80, 2, 28,  256 ),
			new FloppyDiskFormat( 80, 2, 36,  512 ),
			new FloppyDiskFormat( 80, 2, 16,  256 ),
			new FloppyDiskFormat( 80, 2, 26,  128 ),
			new FloppyDiskFormat( 80, 1,  5, 1024 ),
			new FloppyDiskFormat( 80, 1, 16,  256 ),
			new FloppyDiskFormat( 80, 1, 26,  128 ),
			new FloppyDiskFormat( 40, 1, 16,  256 ),
			new FloppyDiskFormat( 40, 2, 16,  256 ),
			new FloppyDiskFormat( 40, 1,  8,  512 ),
			new FloppyDiskFormat( 40, 1,  9,  512 ),
			new FloppyDiskFormat( 40, 2,  8,  512 ),
			new FloppyDiskFormat( 40, 2,  9,  512 ),
			new FloppyDiskFormat( 40, 1,  5, 1024 ),
			new FloppyDiskFormat( 40, 2,  5, 1024 ),
			new FloppyDiskFormat( 40, 2, 26,  128 ),
			new FloppyDiskFormat( 40, 1, 26,  128 ) };

  private static Integer maxDiskSize = null;

  private int     cyls;
  private int     sides;
  private int     sectorsPerTrack;
  private int     sectorSize;
  private int     interleave;
  private int     diskSize;
  private int     sysTracks;
  private int     dirBlocks;
  private int     blockSize;
  private boolean blockNum16Bit;
  private boolean dateStamper;
  private String  infoText;


  public FloppyDiskFormat(
		int     cyls,
		int     sides,
		int     sectorsPerTrack,
		int     sectorSize,
		int     interleave,
		int     sysTracks,
                int     dirBlocks,
                int     blockSize,
                boolean blockNum16Bit,
		boolean dateStamper,
		String  infoText )
  {
    this.cyls            = cyls;
    this.sides           = sides;
    this.sectorsPerTrack = sectorsPerTrack;
    this.sectorSize      = sectorSize;
    this.interleave      = interleave;
    this.diskSize        = cyls * sides * sectorsPerTrack * sectorSize;
    this.sysTracks       = sysTracks;
    this.dirBlocks       = dirBlocks;
    this.blockSize       = blockSize;
    this.blockNum16Bit   = blockNum16Bit;
    this.dateStamper     = dateStamper;
    if( infoText != null ) {
      this.infoText = infoText;
    } else {
      StringBuilder buf = new StringBuilder( 128 );
      buf.append( LangUtil.getText(
			"disk.text.kbyte_tracks_bytes",
			diskSize / 1024,
			cyls,
			sectorsPerTrack,
			sectorSize ) );
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
      this.infoText = buf.toString();
    }
  }


  public FloppyDiskFormat(
			int cyls,
			int sides,
			int sectorsPerTrack,
			int sectorSize )
  {
    this(
	cyls,
	sides,
	sectorsPerTrack,
	sectorSize,
	1,
	-1,
	-1,
	-1,
	true,
	false,
	null );
  }


  public int getBlockSize()
  {
    return this.blockSize;
  }


  public int getCylinders()
  {
    return this.cyls;
  }


  public int getDirBlocks()
  {
    return this.dirBlocks;
  }


  public int getDiskSize()
  {
    return this.diskSize;
  }


  private static FloppyDiskFormat getFormat(
					int cyls,
					int sides,
					int sectorsPerTrack,
					int sectorSize )
  {
    FloppyDiskFormat rv = null;
    for( int i= 0; i < formats.length; i++ ) {
      FloppyDiskFormat fmt = formats[ i ];
      if( (fmt.getCylinders() == cyls)
	  && (fmt.getSides() == sides)
	  && (fmt.getSectorsPerTrack() == sectorsPerTrack)
	  && (fmt.getSectorSize() == sectorSize) )
      {
	rv = fmt;
	break;
      }
    }
    return rv;
  }


  public static FloppyDiskFormat getFormatByDiskSize( long diskSize )
  {
    FloppyDiskFormat rv = null;
    if( diskSize > 0 ) {
      for( int i= 0; i < formats.length; i++ ) {
	if( formats[ i ].getDiskSize() == diskSize ) {
	  if( rv != null ) {
	    rv = null;
	    break;		// Format nicht eindeutig
	  }
	  rv = formats[ i ];
	}
      }
    }
    return rv;
  }


  public static FloppyDiskFormat[] getFormats()
  {
    return formats;
  }


  public int getInterleave()
  {
    return this.interleave;
  }


  public static int getMaxDiskSize()
  {
    if( maxDiskSize == null ) {
      int m = 0;
      for( int i = 0; i < formats.length; i++ ) {
	int diskSize = formats[ i ].getDiskSize();
	if( diskSize > m ) {
	  m = diskSize;
	}
      }
      maxDiskSize = m;
    }
    return maxDiskSize.intValue();
  }


  public int getSectorsPerTrack()
  {
    return this.sectorsPerTrack;
  }


  public int getSectorSize()
  {
    return this.sectorSize;
  }


  public int getSides()
  {
    return this.sides;
  }


  public int getSysTracks()
  {
    return this.sysTracks;
  }


  public boolean isBlockNum16Bit()
  {
    return this.blockNum16Bit;
  }


  public boolean isDateStamperEnabled()
  {
    return this.dateStamper;
  }


  public boolean isHD()
  {
    return ((this.sectorsPerTrack * this.sectorSize) > 7000);
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  /*
   * Die Bezeichnung wird in Auswahlfeldern angezeigt
   * und deshalb uebersetzt zurueckgeliefert.
   */
  public String toString()
  {
    return this.infoText != null ? LangUtil.getText( this.infoText ) : "";
  }
}
