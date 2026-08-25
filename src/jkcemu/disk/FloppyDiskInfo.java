/*
 * (c) 2009-2019 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Information ueber eine Diskette
 */

package jkcemu.disk;

import java.awt.Frame;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import jkcemu.base.EmuUtil;
import jkcemu.lang.LangUtil;


public class FloppyDiskInfo implements Comparable<FloppyDiskInfo>
{
  private String  resource;
  private String  infoText;
  private int     sysTracks;
  private int     blockSize;
  private boolean blockNum16Bit;


  public FloppyDiskInfo(
		String  resource,
		String  infoText,
		int     sysTracks,
		int     blockSize,
		boolean blockNum16Bit )
  {
    this.resource      = resource;
    this.infoText      = infoText;
    this.sysTracks     = sysTracks;
    this.blockSize     = blockSize;
    this.blockNum16Bit = blockNum16Bit;
  }


  public boolean getBlockNum16Bit()
  {
    return this.blockNum16Bit;
  }


  public int getBlockSize()
  {
    return this.blockSize;
  }


  public String getResource()
  {
    return this.resource;
  }


  public int getSysTracks()
  {
    return this.sysTracks;
  }


  public AbstractFloppyDisk openDisk( Frame owner ) throws IOException
  {
    AbstractFloppyDisk disk = null;
    InputStream        in   = null;
    GZIPInputStream    gz   = null;
    try {
      in = getClass().getResourceAsStream( this.resource );
      if( in == null ) {
	throw new IOException( "Resource " + this.resource
				+ " kann nicht ge\u00F6ffnet werden" );
      }
      if( this.resource.endsWith( ".dump.gz" ) ) {
	gz   = new GZIPInputStream( in );
	disk = AnaDisk.readResourceStream( owner, gz, this.resource );
      }
    }
    finally {
      EmuUtil.closeSilently( gz );
      EmuUtil.closeSilently( in );
    }
    if( disk == null ) {
      throw new IOException( "Resource " + this.resource
				+ " kann nicht gelesen werden" );
    }
    return disk;
  }


	/* --- Comparable --- */

  /*
   * Sortiert wird nach der angezeigten, d.h. uebersetzten Bezeichnung,
   * damit die Auswahlliste in der eingestellten Sprache
   * alphabetisch sortiert ist.
   */
  @Override
  public int compareTo( FloppyDiskInfo info )
  {
    return toString().compareTo( info.toString() );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String toString()
  {
    return this.infoText != null ? LangUtil.tr( this.infoText ) : "";
  }
}
