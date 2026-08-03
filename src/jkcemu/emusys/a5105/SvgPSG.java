/*
 * (c) 2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des im Speicherverwaltungsschaltkreis SVG
 * implementierten Sound-Generators des A5105.
 *
 * Die Registerbelegung ist mit einer Ausnahme identisch zum AY-3-8910:
 * Waehrend im Original die Bits im Register 7 L-aktiv sind,
 * sind sie im A5105 H-aktiv.
 * Aus diesem Grund wird hier die Emulation des AY-3-8910 verwendet
 * und nur das Register 7 negiert.
 */

package jkcemu.emusys.a5105;

import jkcemu.etc.PSG8910;


public class SvgPSG extends PSG8910
{
  public SvgPSG( int clockHz, Callback callback )
  {
    super( clockHz, callback );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public int getRegValue( int regNum )
  {
    int rv = super.getRegValue( regNum );
    if( regNum == 7 ) {
      rv = ~rv & 0x3F;
    }
    return rv;
  }


  @Override
  public void setRegValue( int regNum, int value )
  {
    if( regNum == 7 ) {
      value = ~value & 0x3F;
    }
    super.setRegValue( regNum, value );
  }
}
