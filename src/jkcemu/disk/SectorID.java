/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Daten einer Sektor-ID
 */

package jkcemu.disk;


public class SectorID implements Comparable<SectorID>
{
  private int    cyl;
  private int    head;
  private int    sectorNum;
  private int    sizeCode;
  private String idString;


  public SectorID(
		int cyl,
		int head,
		int sectorNum,
		int sizeCode )
  {
    this.cyl       = cyl;
    this.head      = head;
    this.sectorNum = sectorNum;
    this.sizeCode  = sizeCode;
    this.idString  = null;
  }


  public boolean equalsSectorID(
			int cyl,
			int head,
			int sectorNum,
			int sizeCode )
  {
    return (cyl == this.cyl)
		&& (head == this.head)
		&& (sectorNum == this.sectorNum)
		&& (sizeCode == this.sizeCode);
  }


  public int getCylinder()
  {
    return this.cyl;
  }


  public int getHead()
  {
    return this.head;
  }


  public String getIDString()
  {
    if( this.idString == null ) {
      this.idString = String.format(
				"[%d:%d:%d:%d]",
				this.cyl,
				this.head,
				this.sectorNum,
				this.sizeCode );
    }
    return this.idString;
  }


  public int getSectorNum()
  {
    return this.sectorNum;
  }


  public int getSizeCode()
  {
    return this.sizeCode;
  }


  protected void setSectorID( int cyl, int head, int sectorNum )
  {
    this.cyl       = cyl;
    this.head      = head;
    this.sectorNum = sectorNum;
  }


  protected void setSizeCode( int sizeCode )
  {
    this.sizeCode = sizeCode;
  }


	/* --- Comparable --- */

  @Override
  public int compareTo( SectorID sectorID )
  {
    int rv = this.cyl - sectorID.getCylinder();
    if( rv == 0 ) {
      rv = this.head - sectorID.getHead();
    }
    if( rv == 0 ) {
      rv = this.sectorNum - sectorID.getSectorNum();
    }
    if( rv == 0 ) {
      rv = this.sizeCode - sectorID.getSizeCode();
    }
    return rv;
  }
}
