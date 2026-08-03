/*
 * (c) 2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Basisklasse zur Emulation einer Diskette
 * mit einem regulaerem Format (gleichmaessige Sektoranordnung)
 */

package jkcemu.disk;

import java.awt.Frame;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class RegularFormatFloppyDisk extends AbstractFloppyDisk
{
  public static final String PROP_SECTORS_PER_TRACK = "sectors_per_track";
  public static final String PROP_SECTORSIZE        = "sectorsize";


  private volatile int sectorsPerTrack;
  private volatile int sectorSize;


  protected RegularFormatFloppyDisk(
			Frame owner,
			int   cyls,
			int   sides,
			int   sectorsPerTrack,
			int   sectorSize,
			int   interleave )
  {
    super( owner, cyls, sides, interleave );
    this.sectorsPerTrack = sectorsPerTrack;
    this.sectorSize      = sectorSize;
  }


  protected RegularFormatFloppyDisk(
			Frame owner,
			int   cyls,
			int   sides,
			int   sectorsPerTrack,
			int   sectorSize )
  {
    this( owner, cyls, sides, sectorsPerTrack, sectorSize, 0 );
  }


  protected int getSectorSize()
  {
    return this.sectorSize;
  }


  protected int getSectorsPerTrack()
  {
    return this.sectorsPerTrack;
  }


  protected void setSectorSize( int sectorSize )
  {
    this.sectorSize = sectorSize;
  }


  protected void setSectorsPerTrack( int sectorsPerTrack )
  {
    this.sectorsPerTrack = sectorsPerTrack;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected Density determineDiskDensity()
  {
    return getDensityByTrackSize( this.sectorsPerTrack * this.sectorSize );
  }


  @Override
  protected int getDiskSize(
			Set<Integer> sectorsPerTracksOut,
			Set<Integer> sectorSizesOut )
  {
    int diskSize = 0;
    int cyls     = getCylinders();
    int sides    = getSides();
    if( (cyls > 0) && (sides > 0)
	&& (this.sectorsPerTrack > 0) && (this.sectorSize > 0) )
    {
      diskSize = getCylinders()
			* getSides()
			* this.sectorsPerTrack
			* this.sectorSize;
      if( sectorsPerTracksOut != null ) {
	sectorsPerTracksOut.add( this.sectorsPerTrack );
      }
      if( sectorSizesOut != null ) {
	sectorSizesOut.add( this.sectorSize );
      }
    }
    return diskSize;
  }


  @Override
  public int getMostCommonSectorSize(
			AtomicInteger rvOccurence,
			AtomicInteger rvTotalSectorCnt )
  {
    int   sectorSize = 0;
    float occurence  = 0F;
    if( (getCylinders() > 0) && (getSides() > 0)
	&& (this.sectorsPerTrack > 0) )
    {
      sectorSize = this.sectorSize;
      occurence  = 1F;
    }
    return sectorSize;
  }


  @Override
  public int getTrackSectorCount( int physCyl, int physHead )
  {
    int cyls  = getCylinders();
    int sides = getSides();
    return (physCyl >= 0) && (physCyl < cyls)
	   && (physHead < sides) && (sides >= 1) ? this.sectorsPerTrack : 0;
  }


  @Override
  public void putSettingsTo( Properties props, String prefix )
  {
    super.putSettingsTo( props, prefix );
    if( props != null ) {
      props.setProperty(
		prefix + PROP_SECTORS_PER_TRACK,
		Integer.toString( getSectorsPerTrack() ) );
      props.setProperty(
		prefix + PROP_SECTORSIZE,
		Integer.toString( getSectorSize() ) );
    }
  }
}
